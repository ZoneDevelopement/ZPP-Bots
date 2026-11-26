package dev.nandi0813.zppbots.platform;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/** Builds the two tiny native subclasses needed by the bot platform. */
final class NativeBotAdapters {

    private static final ClassDesc CD_VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc CD_BOOLEAN = ClassDesc.ofDescriptor("Z");
    private static final ClassDesc CD_OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc CD_CONSUMER = ClassDesc.of("java.util.function.Consumer");

    private NativeBotAdapters() {
    }

    static synchronized Types create(Class<?> connectionClass, Class<?> packetFlowClass, Class<?> packetClass,
                                     Class<?> listenerCallbackClass, Class<?> listenerImplClass,
                                     Class<?> serverClass, Class<?> playerClass, Class<?> cookieClass,
                                     Class<?> serverPlayerClass, Class<?> serverLevelClass,
                                     Class<?> gameProfileClass, Class<?> clientInformationClass)
            throws ReflectiveOperationException {
        Constructor<?> connectionConstructor = connectionClass.getConstructor(packetFlowClass);
        validateConstructor(connectionConstructor);
        validateMethod(connectionClass.getMethod("send", packetClass), void.class, packetClass);
        validateMethod(connectionClass.getMethod("send", packetClass, listenerCallbackClass), void.class,
                packetClass, listenerCallbackClass);
        validateMethod(connectionClass.getMethod("send", packetClass, listenerCallbackClass, boolean.class),
                void.class, packetClass, listenerCallbackClass, boolean.class);

        validateMethod(listenerImplClass.getMethod("hasClientLoaded"), boolean.class);
        validateMethod(listenerImplClass.getMethod("tickClientLoadTimeout"), void.class);
        Constructor<?> loadedConstructor = listenerImplClass.getConstructor(serverClass, connectionClass,
                playerClass, cookieClass);
        validateConstructor(loadedConstructor);
        Constructor<?> nativePlayerConstructor = serverPlayerClass.getConstructor(serverClass, serverLevelClass,
                gameProfileClass, clientInformationClass);
        validateConstructor(nativePlayerConstructor);
        validateMethod(serverPlayerClass.getMethod("isClientAuthoritative"), boolean.class);

        String packageName = NativeBotAdapters.class.getPackageName();
        Class<?> sinkClass = loadOrDefine(packageName + ".ZPBotSinkConnection", connectionClass,
                sinkBytes(packageName, connectionClass, packetFlowClass, packetClass, listenerCallbackClass));
        Class<?> loadedClass = loadOrDefine(packageName + ".ZPBotLoadedListener", listenerImplClass,
                loadedBytes(packageName, listenerImplClass, serverClass, connectionClass, playerClass, cookieClass));
        Class<?> botPlayerClass = loadOrDefine(packageName + ".ZPBotServerPlayer", serverPlayerClass,
                playerBytes(packageName, serverPlayerClass, serverLevelClass, serverClass, gameProfileClass,
                        clientInformationClass));
        return new Types(sinkClass, loadedClass, botPlayerClass);
    }

    private static Class<?> loadOrDefine(String name, Class<?> superclass, byte[] bytes)
            throws ReflectiveOperationException {
        try {
            Class<?> loaded = Class.forName(name, false, NativeBotAdapters.class.getClassLoader());
            if (loaded.getSuperclass() != superclass) {
                throw new LinkageError("Existing adapter has unexpected superclass: " + name);
            }
            return loaded;
        } catch (ClassNotFoundException ignored) {
            return MethodHandles.lookup().defineClass(bytes);
        }
    }

    private static byte[] sinkBytes(String packageName, Class<?> superclass, Class<?> packetFlowClass,
                                    Class<?> packetClass, Class<?> callbackClass) {
        ClassDesc owner = ClassDesc.of(packageName + ".ZPBotSinkConnection");
        ClassDesc parent = ClassDesc.of(superclass.getName());
        ClassDesc packet = ClassDesc.of(packetClass.getName());
        ClassDesc callback = ClassDesc.of(callbackClass.getName());
        ClassDesc flow = ClassDesc.of(packetFlowClass.getName());
        ClassDesc consumer = CD_CONSUMER;
        ClassFile classFile = ClassFile.of();
        return classFile.build(owner, cb -> {
            cb.withFlags(Modifier.PUBLIC | Modifier.FINAL | 0x0020);
            cb.withSuperclass(parent);
            cb.withField("completion", consumer, fb -> fb.withFlags(Modifier.PRIVATE | Modifier.FINAL));
            cb.withField("packets", consumer, fb -> fb.withFlags(Modifier.PRIVATE | Modifier.FINAL));
            cb.withMethod("<init>", MethodTypeDesc.of(CD_VOID, flow, consumer, consumer), Modifier.PUBLIC, mb ->
                    mb.withCode(code -> code.aload(0).aload(1).invokespecial(parent, "<init>",
                                    MethodTypeDesc.of(CD_VOID, flow))
                            .aload(0).aload(2).putfield(owner, "completion", consumer)
                            .aload(0).aload(3).putfield(owner, "packets", consumer).return_()));
            MethodTypeDesc notifyType = MethodTypeDesc.of(CD_VOID, CD_OBJECT);
            cb.withMethod("send", MethodTypeDesc.of(CD_VOID, packet), Modifier.PUBLIC, mb ->
                    mb.withCode(code -> code.aload(0).getfield(owner, "packets", consumer).aload(1)
                            .invokeinterface(consumer, "accept", notifyType).return_()));
            cb.withMethod("send", MethodTypeDesc.of(CD_VOID, packet, callback), Modifier.PUBLIC, mb ->
                    mb.withCode(code -> code.aload(0).getfield(owner, "packets", consumer).aload(1)
                            .invokeinterface(consumer, "accept", notifyType)
                            .aload(0).getfield(owner, "completion", consumer).aload(2)
                            .invokeinterface(consumer, "accept", notifyType).return_()));
            cb.withMethod("send", MethodTypeDesc.of(CD_VOID, packet, callback, CD_BOOLEAN), Modifier.PUBLIC, mb ->
                    mb.withCode(code -> code.aload(0).getfield(owner, "packets", consumer).aload(1)
                            .invokeinterface(consumer, "accept", notifyType)
                            .aload(0).getfield(owner, "completion", consumer).aload(2)
                            .invokeinterface(consumer, "accept", notifyType).return_()));
        });
    }

    private static byte[] playerBytes(String packageName, Class<?> superclass, Class<?> levelClass,
                                      Class<?> serverClass, Class<?> profileClass, Class<?> informationClass) {
        ClassDesc owner = ClassDesc.of(packageName + ".ZPBotServerPlayer");
        ClassDesc parent = ClassDesc.of(superclass.getName());
        ClassDesc level = ClassDesc.of(levelClass.getName());
        ClassDesc server = ClassDesc.of(serverClass.getName());
        ClassDesc profile = ClassDesc.of(profileClass.getName());
        ClassDesc information = ClassDesc.of(informationClass.getName());
        ClassFile classFile = ClassFile.of();
        return classFile.build(owner, cb -> {
            cb.withFlags(Modifier.PUBLIC | Modifier.FINAL | 0x0020);
            cb.withSuperclass(parent);
            cb.withMethod("<init>", MethodTypeDesc.of(CD_VOID, server, level, profile, information),
                    Modifier.PUBLIC, mb -> mb.withCode(code -> code.aload(0).aload(1).aload(2).aload(3).aload(4)
                            .invokespecial(parent, "<init>", MethodTypeDesc.of(CD_VOID, server, level, profile,
                                    information)).return_()));
            cb.withMethod("isClientAuthoritative", MethodTypeDesc.of(CD_BOOLEAN), Modifier.PUBLIC,
                    mb -> mb.withCode(code -> code.iconst_0().ireturn()));
        });
    }

    private static byte[] loadedBytes(String packageName, Class<?> superclass, Class<?> serverClass,
                                      Class<?> connectionClass, Class<?> playerClass, Class<?> cookieClass) {
        ClassDesc owner = ClassDesc.of(packageName + ".ZPBotLoadedListener");
        ClassDesc parent = ClassDesc.of(superclass.getName());
        ClassDesc server = ClassDesc.of(serverClass.getName());
        ClassDesc connection = ClassDesc.of(connectionClass.getName());
        ClassDesc player = ClassDesc.of(playerClass.getName());
        ClassDesc cookie = ClassDesc.of(cookieClass.getName());
        ClassFile classFile = ClassFile.of();
        return classFile.build(owner, cb -> {
            cb.withFlags(Modifier.PUBLIC | Modifier.FINAL | 0x0020);
            cb.withSuperclass(parent);
            cb.withMethod("<init>", MethodTypeDesc.of(CD_VOID, server, connection, player, cookie), Modifier.PUBLIC,
                    mb -> mb.withCode(code -> code.aload(0).aload(1).aload(2).aload(3).aload(4)
                            .invokespecial(parent, "<init>", MethodTypeDesc.of(CD_VOID, server, connection, player,
                                    cookie)).return_()));
            cb.withMethod("hasClientLoaded", MethodTypeDesc.of(CD_BOOLEAN), Modifier.PUBLIC,
                    mb -> mb.withCode(code -> code.iconst_1().ireturn()));
            cb.withMethod("tickClientLoadTimeout", MethodTypeDesc.of(CD_VOID), Modifier.PUBLIC,
                    mb -> mb.withCode(code -> code.return_()));
        });
    }

    private static void validateConstructor(Constructor<?> constructor) throws NoSuchMethodException {
        if (!Modifier.isPublic(constructor.getModifiers())) {
            throw new NoSuchMethodException("Constructor is not public: " + constructor);
        }
    }

    private static void validateMethod(java.lang.reflect.Method method, Class<?> returnType, Class<?>... parameters)
            throws NoSuchMethodException {
        int modifiers = method.getModifiers();
        if (method.getReturnType() != returnType || !java.util.Arrays.equals(method.getParameterTypes(), parameters)
                || !Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            throw new NoSuchMethodException("Expected public overridable method: " + method);
        }
    }

    record Types(Class<?> connection, Class<?> listener, Class<?> player) {
    }
}
