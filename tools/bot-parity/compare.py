#!/usr/bin/env python3
"""Validate and compare bounded ZonePractice movement JSONL traces."""

import argparse
import json
import math
import sys
import uuid
from pathlib import Path

MAX_CHARS = 1_048_576
MAX_BYTES = MAX_CHARS * 4
MAX_LINE = 16_384
MAX_SAMPLES = 600
MAX_EVENTS = 2_400
INPUTS = ("forward", "backward", "left", "right", "jump", "sneak", "sprint")
STATES = ("onGround", "sprinting", "sneaking", "dead", "blocking", "handRaised")
TOLERANCES = ("positionMax", "positionRms", "velocityMax", "velocityRms", "yawMax", "pitchMax")
REQUIRED_MATCH_FIELDS = (
    "equipment", "attributes", "effects", "gameMode", "food", "saturation", "health", "exhaustion"
)


class TraceError(ValueError):
    pass


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise TraceError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _constant(value):
    raise TraceError(f"invalid JSON constant: {value}")


def _finite_number(value, where):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TraceError(f"{where} must be finite numeric")
    try:
        number = float(value)
    except (OverflowError, ValueError) as exc:
        raise TraceError(f"{where} must be finite numeric") from exc
    if not math.isfinite(number):
        raise TraceError(f"{where} must be finite numeric")
    return number


def _bool_fields(value, names, where):
    if not isinstance(value, dict):
        raise TraceError(f"{where} must be an object")
    for name in names:
        if name not in value or type(value[name]) is not bool:
            raise TraceError(f"{where}.{name} must be bool")


def _uuid(value, where):
    if not isinstance(value, str):
        raise TraceError(f"{where} must be a UUID")
    try:
        return str(uuid.UUID(value))
    except ValueError as exc:
        raise TraceError(f"{where} must be a UUID") from exc


def _vector(value, where):
    if not isinstance(value, dict) or set(value) != {"x", "y", "z"}:
        raise TraceError(f"{where} must contain exactly x, y and z")
    return tuple(_finite_number(value[axis], f"{where}.{axis}") for axis in ("x", "y", "z"))


def _attributes(value, where):
    if not isinstance(value, dict) or "movementSpeed" not in value:
        raise TraceError(f"{where} must contain movementSpeed")
    if value["movementSpeed"] is None:
        raise TraceError(f"{where}.movementSpeed must be numeric")
    for key, item in value.items():
        if item is not None:
            _finite_number(item, f"{where}.{key}")


def _read_jsonl(path):
    try:
        with Path(path).open("rb") as stream:
            raw = stream.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES:
            raise TraceError("trace exceeds UTF-8 byte bound")
        content = raw.decode("utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise TraceError(f"cannot read UTF-8 trace {path}: {exc}") from exc
    if len(content) > MAX_CHARS:
        raise TraceError("trace exceeds 1,048,576 characters")
    lines = content.splitlines()
    if not lines or any(not line for line in lines):
        raise TraceError("trace must contain nonempty JSONL records")
    if any(len(line) > MAX_LINE for line in lines):
        raise TraceError("trace line exceeds 16,384 characters")
    records = []
    for number, line in enumerate(lines, 1):
        try:
            record = json.loads(line, object_pairs_hook=_pairs, parse_constant=_constant)
        except ValueError as exc:
            raise TraceError(f"line {number}: {exc}") from exc
        if not isinstance(record, dict) or record.get("type") not in {"metadata", "sample", "swing", "damage", "summary"}:
            raise TraceError(f"line {number}: unknown record type")
        records.append(record)
    return records


def validate_trace(path):
    records = _read_jsonl(path)
    if records[0]["type"] != "metadata" or records[-1]["type"] != "summary":
        raise TraceError("trace must start with metadata and end with summary")
    if sum(record["type"] == "metadata" for record in records) != 1:
        raise TraceError("trace must contain exactly one metadata record")
    if sum(record["type"] == "summary" for record in records) != 1:
        raise TraceError("trace must contain exactly one summary record")
    metadata, summary = records[0], records[-1]
    if metadata.get("schema") != 2 or metadata.get("samplePhase") != "server-tick-end":
        raise TraceError("incompatible schema or sample phase")
    for key in ("kind", "label", "minecraftVersion", "serverVersion", "pluginVersion", "plugins", "session", "requestedSamples", "startTick"):
        if key not in metadata:
            raise TraceError(f"metadata missing {key}")
    if metadata["kind"] not in {"human-reference", "managed-candidate"}:
        raise TraceError("invalid metadata.kind")
    if not all(isinstance(metadata[key], str) and metadata[key] for key in ("label", "minecraftVersion", "serverVersion", "pluginVersion")):
        raise TraceError("metadata label and versions must be nonempty strings")
    if not isinstance(metadata["plugins"], list) or not all(
        isinstance(item, dict) and isinstance(item.get("name"), str) and item["name"]
        and isinstance(item.get("version"), str) and item["version"] for item in metadata["plugins"]
    ):
        raise TraceError("metadata.plugins must contain name/version objects")
    if not isinstance(metadata["session"], str) or not metadata["session"]:
        raise TraceError("metadata.session must be a nonempty string")
    for key in ("requestedSamples", "startTick"):
        if type(metadata[key]) is not int or metadata[key] < 0:
            raise TraceError(f"metadata.{key} must be a nonnegative integer")
    samples = [record for record in records if record["type"] == "sample"]
    events = [record for record in records if record["type"] in {"swing", "damage"}]
    if not samples or len(samples) > MAX_SAMPLES or len(events) > MAX_EVENTS:
        raise TraceError("sample/event count is empty or exceeds trace limits")
    if summary.get("truncated") is not False or summary.get("reason") != "COMPLETED":
        raise TraceError("incomplete, truncated, LIMIT or ERROR trace")
    if type(summary.get("samples")) is not int or summary["samples"] != len(samples):
        raise TraceError("summary sample count mismatch")
    if metadata["requestedSamples"] != len(samples):
        raise TraceError("requested sample count mismatch")
    end_tick = summary.get("endTick")
    if type(end_tick) is not int or end_tick < metadata["startTick"]:
        raise TraceError("invalid summary.endTick")
    if type(summary.get("events")) is not int:
        raise TraceError("summary.events must be an integer")
    if summary["events"] != len(events):
        raise TraceError("summary event count mismatch")

    previous_tick = None
    world = None
    for index, sample in enumerate(samples):
        if sample.get("sample") != index or type(sample.get("sample")) is not int:
            raise TraceError("sample indexes must be contiguous from zero")
        tick, elapsed = sample.get("tick"), sample.get("elapsedTicks")
        if type(tick) is not int or type(elapsed) is not int:
            raise TraceError("sample tick and elapsedTicks must be integers")
        if index == 0:
            if tick not in {metadata["startTick"], metadata["startTick"] + 1}:
                raise TraceError("native ticks must begin at startTick or startTick+1")
        elif tick != previous_tick + 1:
            raise TraceError("sample ticks must be strictly consecutive")
        if elapsed != tick - metadata["startTick"]:
            raise TraceError("sample elapsedTicks must equal tick-startTick")
        sample_world = _uuid(sample.get("world"), "sample.world")
        if index == 0:
            world = sample_world
        elif sample_world != world:
            raise TraceError("sample world changed during trace")
        _vector(sample.get("position"), "sample.position")
        _vector(sample.get("velocity"), "sample.velocity")
        for key in ("yaw", "pitch"):
            _finite_number(sample.get(key), f"sample.{key}")
        _attributes(sample.get("attributes"), "sample.attributes")
        _bool_fields(sample.get("input"), INPUTS, "sample.input")
        _bool_fields(sample, STATES, "sample state")
        for key in ("gameMode",):
            if not isinstance(sample.get(key), str):
                raise TraceError(f"sample.{key} must be a string")
        for key in ("food",):
            if type(sample.get(key)) is not int:
                raise TraceError(f"sample.{key} must be an integer")
        for key in ("saturation", "health", "exhaustion"):
            _finite_number(sample.get(key), f"sample.{key}")
        if not isinstance(sample.get("equipment"), dict) or not isinstance(sample.get("effects"), list):
            raise TraceError("sample equipment/effects must be an object/list")
        previous_tick = tick
    if end_tick != samples[-1]["tick"]:
        raise TraceError("summary endTick does not match final sample")
    for event in events:
        tick = event.get("tick")
        if type(tick) is not int or not metadata["startTick"] <= tick <= end_tick:
            raise TraceError("event tick outside trace interval")
        if event["type"] not in {"swing", "damage"}:
            raise TraceError("unknown event type")
    metadata["world"] = world
    return {"metadata": metadata, "summary": summary, "samples": samples, "events": events}


def _transitions(samples):
    out = []
    previous = None
    for index, sample in enumerate(samples):
        controls = tuple(sample["input"][name] for name in INPUTS)
        if controls != previous:
            out.append(index)
            previous = controls
    return out


def _distance(a, b):
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


def _angle_distance(a, b):
    return abs((a - b + 180.0) % 360.0 - 180.0)


def _rms(values):
    return math.sqrt(sum(value * value for value in values) / len(values))


def _tolerances(path):
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=_pairs, parse_constant=_constant)
    except (OSError, UnicodeDecodeError, ValueError) as exc:
        raise TraceError(f"cannot read tolerances: {exc}") from exc
    return _tolerances_from_data(data)


def _tolerances_from_data(data):
    if not isinstance(data, dict) or set(data) != set(TOLERANCES) | {"provenance"}:
        raise TraceError("tolerances must contain exactly six limits and provenance")
    for key in TOLERANCES:
        _finite_number(data[key], f"tolerances.{key}")
        if data[key] < 0:
            raise TraceError(f"tolerances.{key} must be nonnegative")
    if not isinstance(data["provenance"], str) or not data["provenance"].strip():
        raise TraceError("tolerances.provenance must identify repeated real-client trials")
    return data


def compare(reference, candidate, limits):
    ref, cand = validate_trace(reference), validate_trace(candidate)
    rm, cm = ref["metadata"], cand["metadata"]
    if rm["kind"] != "human-reference" or cm["kind"] != "managed-candidate":
        raise TraceError("comparison requires human-reference and managed-candidate kinds")
    for key in ("label", "minecraftVersion", "serverVersion", "pluginVersion", "plugins", "world"):
        if rm[key] != cm[key]:
            raise TraceError(f"incompatible metadata.{key}")
    rs, cs = ref["samples"], cand["samples"]
    if len(rs) != len(cs):
        raise TraceError("sample counts differ")
    for key in REQUIRED_MATCH_FIELDS:
        if rs[0][key] != cs[0][key]:
            raise TraceError(f"incompatible first-sample {key}")
    position, velocity, yaw, pitch = [], [], [], []
    mismatch_counts = {key: 0 for key in INPUTS + STATES + ("gameMode",)}
    for a, b in zip(rs, cs):
        for key in INPUTS:
            mismatch_counts[key] += a["input"][key] != b["input"][key]
        for key in STATES + ("gameMode",):
            mismatch_counts[key] += a[key] != b[key]
        pos_a, pos_b = _vector(a["position"], "reference.position"), _vector(b["position"], "candidate.position")
        vel_a, vel_b = _vector(a["velocity"], "reference.velocity"), _vector(b["velocity"], "candidate.velocity")
        pos_a0 = _vector(rs[0]["position"], "reference.position")
        pos_b0 = _vector(cs[0]["position"], "candidate.position")
        position.append(_distance(tuple(x - o for x, o in zip(pos_a, pos_a0)), tuple(x - o for x, o in zip(pos_b, pos_b0))))
        velocity.append(_distance(vel_a, vel_b))
        yaw.append(_angle_distance(a["yaw"], b["yaw"]))
        pitch.append(abs(a["pitch"] - b["pitch"]))
    metrics = {
        "positionMax": max(position), "positionRms": _rms(position),
        "velocityMax": max(velocity), "velocityRms": _rms(velocity),
        "yawMax": max(yaw), "pitchMax": max(pitch),
    }
    passed = all(metrics[key] <= limits[key] for key in TOLERANCES) and not any(mismatch_counts.values())
    return {
        "status": "passed" if passed else "diverged",
        "passed": passed,
        "metrics": metrics,
        "tolerances": limits,
        "sessions": {"reference": rm["session"], "candidate": cm["session"]},
        "mismatchCounts": mismatch_counts,
        "inputTransitions": {"reference": _transitions(rs), "candidate": _transitions(cs)},
    }


def _record(kind, index, tick, origin, input_value=None, xoffset=0.0):
    return {
        "type": "sample", "sample": index, "tick": tick, "elapsedTicks": index + 1,
        "world": "00000000-0000-0000-0000-000000000001",
        "position": {"x": origin + xoffset, "y": 64.0, "z": 0.0},
        "velocity": {"x": xoffset, "y": 0.0, "z": 0.0}, "yaw": 0.0, "pitch": 0.0,
        "attributes": {"movementSpeed": 0.1, "optional": None},
        "input": input_value or dict.fromkeys(INPUTS, False),
        "onGround": True, "sprinting": False, "sneaking": False, "dead": False,
        "blocking": False, "handRaised": False, "gameMode": "SURVIVAL", "food": 20,
        "saturation": 5.0, "health": 20.0, "exhaustion": 0.0,
        "equipment": {}, "effects": [],
    }


def _trace(kind, tick_origin=10, origin=0.0, divergence=0.0, controls=None, complete=True):
    samples = [_record(kind, i, tick_origin + i + 1, origin, controls if i == 1 else None, divergence if i > 1 else 0.0) for i in range(3)]
    return [
        {"type": "metadata", "schema": 2, "samplePhase": "server-tick-end", "kind": kind,
         "label": "walk", "minecraftVersion": "test", "serverVersion": "test", "pluginVersion": "test",
         "plugins": [{"name": "ZonePractice", "version": "1.0"}], "session": kind,
         "requestedSamples": len(samples), "startTick": tick_origin},
        samples[0],
        {"type": "swing", "tick": tick_origin + 1, "elapsedTicks": 1, "animation": "ARM_SWING"},
        samples[1],
        {"type": "damage", "tick": tick_origin + 2, "elapsedTicks": 2, "cause": "ENTITY_ATTACK", "damage": 1.0},
        samples[2],
        {"type": "summary", "samples": len(samples), "events": 2, "truncated": not complete,
         "reason": "COMPLETED" if complete else "LIMIT", "endTick": samples[-1]["tick"]},
    ]


def self_check():
    import subprocess
    import tempfile

    limits = {key: 0.0 for key in TOLERANCES} | {"provenance": "repeated reference trials"}

    def assert_cli_invalid(directory, name, content, args=None):
        trace_path = Path(directory) / f"{name}.jsonl"
        trace_path.write_text(content, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve()), *(args or ["--validate", str(trace_path)])],
            capture_output=True, text=True, check=False,
        )
        assert result.returncode == 2, f"{name} returned {result.returncode}: {result.stderr}"
        assert "Traceback" not in result.stderr, f"{name} printed a traceback"
        output = json.loads(result.stdout)
        assert output.get("status") == "invalid", f"{name} did not return invalid JSON"

    def jsonl(records):
        return "".join(json.dumps(record, allow_nan=False) + "\n" for record in records)

    ref = _trace("human-reference", 10, 100.0)
    equal = _trace("managed-candidate", 30, -7.0)
    assert compare_records(ref, equal, limits)["passed"]
    divergent = _trace("managed-candidate", 30, -7.0, 0.25)
    assert not compare_records(ref, divergent, limits)["passed"]
    for bad in (_trace("managed-candidate", complete=False),):
        try:
            validate_records(bad)
        except TraceError:
            pass
        else:
            raise AssertionError("incomplete trace accepted")
    for reason in ("LIMIT", "ERROR"):
        bad = _trace("managed-candidate")
        bad[-1]["reason"] = reason
        try:
            validate_records(bad)
        except TraceError:
            pass
        else:
            raise AssertionError(f"{reason} trace accepted")
    gapped = _trace("managed-candidate")
    gapped[3]["tick"] += 1
    wrong_elapsed = _trace("managed-candidate")
    wrong_elapsed[3]["elapsedTicks"] += 1
    bad_counter = _trace("managed-candidate")
    bad_counter[-1]["samples"] = 99
    for bad in (gapped, wrong_elapsed, bad_counter, _trace("managed-candidate")[:-1]):
        try:
            validate_records(bad)
        except TraceError:
            pass
        else:
            raise AssertionError("gapped ticks accepted")
    mismatch = _trace("managed-candidate")
    mismatch[3]["input"]["forward"] = True
    assert not compare_records(ref, mismatch, limits)["passed"]
    try:
        compare_records(_trace("managed-candidate"), equal, limits)
    except TraceError:
        pass
    else:
        raise AssertionError("synthetic reference kind accepted")
    duplicate = '{"type":"metadata","type":"sample"}'
    try:
        json.loads(duplicate, object_pairs_hook=_pairs)
    except TraceError:
        pass
    else:
        raise AssertionError("duplicate keys accepted")
    try:
        _finite_number(float("nan"), "self-check")
    except TraceError:
        pass
    else:
        raise AssertionError("NaN accepted")
    huge_number = _trace("managed-candidate")
    huge_number[1]["position"]["x"] = 10 ** 400
    try:
        validate_records(huge_number)
    except TraceError:
        pass
    else:
        raise AssertionError("huge integer accepted")
    try:
        _tolerances_from_data({})
    except TraceError:
        pass
    else:
        raise AssertionError("missing tolerances accepted")

    with tempfile.TemporaryDirectory() as directory:
        invalid_type = _trace("managed-candidate")
        invalid_type[0]["type"] = []
        assert_cli_invalid(directory, "invalid-type", jsonl(invalid_type))
        invalid_kind = _trace("managed-candidate")
        invalid_kind[0]["kind"] = []
        assert_cli_invalid(directory, "invalid-kind", jsonl(invalid_kind))
        assert_cli_invalid(directory, "nested", "[" * 1100 + "0" + "]" * 1100)

        reference = _trace("human-reference", origin=-1e308)
        reference[3]["position"]["x"] = 1e308
        candidate = _trace("managed-candidate")
        reference_path = Path(directory) / "reference.jsonl"
        candidate_path = Path(directory) / "candidate.jsonl"
        tolerance_path = Path(directory) / "tolerances.json"
        reference_path.write_text(jsonl(reference), encoding="utf-8")
        candidate_path.write_text(jsonl(candidate), encoding="utf-8")
        tolerance_path.write_text(json.dumps(limits, allow_nan=False), encoding="utf-8")
        assert_cli_invalid(
            directory, "distance-overflow", "",
            [str(reference_path), str(candidate_path), "--tolerances", str(tolerance_path)],
        )
    print(json.dumps({"status": "self-check passed"}, sort_keys=True, allow_nan=False))


def validate_records(records):
    """Same validator as files, with in-memory JSONL to keep self-check offline."""
    return _validate_records(records)


def compare_records(reference, candidate, limits):
    import tempfile

    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=True) as left, tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=True) as right:
        for record in reference:
            left.write(json.dumps(record, allow_nan=False) + "\n")
        for record in candidate:
            right.write(json.dumps(record, allow_nan=False) + "\n")
        left.flush()
        right.flush()
        return compare(left.name, right.name, limits)


def _validate_records(records):
    # Serialize through the same parser/validator, without writing fixture files.
    import tempfile

    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=True) as stream:
        for record in records:
            stream.write(json.dumps(record, allow_nan=False) + "\n")
        stream.flush()
        return validate_trace(stream.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--self-check", action="store_true")
    group.add_argument("--validate", metavar="TRACE")
    parser.add_argument("reference", nargs="?")
    parser.add_argument("candidate", nargs="?")
    parser.add_argument("--tolerances")
    args = parser.parse_args()
    try:
        if args.self_check:
            self_check()
            return 0
        if args.validate:
            trace = validate_trace(args.validate)
            print(json.dumps({"status": "valid", "session": trace["metadata"]["session"], "kind": trace["metadata"]["kind"]}, sort_keys=True, allow_nan=False))
            return 0
        if not args.reference or not args.candidate or not args.tolerances:
            raise TraceError("comparison requires REFERENCE CANDIDATE --tolerances FILE")
        limits = _tolerances(args.tolerances)
        result = compare(args.reference, args.candidate, limits)
        print(json.dumps(result, sort_keys=True, allow_nan=False))
        return 0 if result["passed"] else 1
    except (ValueError, TypeError, OverflowError, RecursionError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True, allow_nan=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
