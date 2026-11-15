package dev.krasnokamen.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Audit the selectors shipped by this build against its actual Minecraft dependency. */
class MixinTargetTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECTION = "Lorg/spongepowered/asm/mixin/injection/";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final Set<String> INJECTORS = Set.of("Inject", "Redirect", "ModifyArg", "ModifyArgs",
            "ModifyVariable", "ModifyConstant", "ModifyExpressionValue", "ModifyReturnValue",
            "WrapOperation", "WrapWithCondition", "WrapMethod");

    @Test
    void everyShippedMixinSelectorExistsInTheGameJar() throws IOException {
        Path resources = Path.of(System.getProperty("loader.mixin.resources"));
        List<Path> configs;
        try (Stream<Path> files = Files.walk(resources)) {
            configs = files.filter(p -> p.getFileName().toString().endsWith(".mixins.json")).sorted().toList();
        }
        assertFalse(configs.isEmpty(), "no shipped mixin configurations in " + resources);
        int checked = 0;
        for (Path config : configs) {
            JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            String pkg = json.get("package").getAsString().replace('.', '/');
            for (String section : List.of("mixins", "client", "server")) {
                if (!json.has(section)) continue;
                for (var entry : json.getAsJsonArray(section)) {
                    String name = pkg + "/" + entry.getAsString().replace('.', '/');
                    auditMixin(name);
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "mixin configurations contain no mixins");
    }

    private static void auditMixin(String name) throws IOException {
        ClassNode mixin = readClass(name);
        AnnotationNode annotation = findAnnotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, MIXIN);
        assertNotNull(annotation, name + " has no @Mixin");
        List<String> owners = new ArrayList<>();
        for (Object value : list(annotation, "value")) owners.add(((Type) value).getInternalName());
        for (Object value : list(annotation, "targets")) owners.add(((String) value).replace('.', '/'));
        assertFalse(owners.isEmpty(), name + " has no target classes");
        for (String owner : owners) {
            ClassNode target = readClass(owner);
            for (MethodNode handler : mixin.methods) {
                for (AnnotationNode a : annotations(handler)) {
                    if (a.desc.equals(ACCESSOR)) {
                        auditAccessor(name, handler, a, target);
                    } else if (a.desc.equals(INVOKER)) {
                        String selector = string(a, "value");
                        assertFalse(selector.isEmpty(), name + ": implicit @Invoker name is unsupported");
                        assertSelector(name, selector + handler.desc, target);
                    } else if (a.desc.startsWith(INJECTION)
                            || a.desc.startsWith("Lcom/llamalad7/mixinextras/injector/")) {
                        String kind = a.desc.substring(a.desc.lastIndexOf('/') + 1, a.desc.length() - 1);
                        assertTrue(INJECTORS.contains(kind), name + ": unsupported injector @" + kind);
                        assertTrue(list(a, "target").isEmpty(), name + ": @Desc selectors need audit support");
                        assertTrue(list(a, "slice").isEmpty(), name + ": injection slices need audit support");
                        List<?> selectors = list(a, "method");
                        assertFalse(selectors.isEmpty(), name + ": @" + kind + " has no method selector");
                        for (Object value : selectors) {
                            String selector = (String) value;
                            List<MethodNode> methods = assertSelector(name, selector, target);
                            for (Object at : list(a, "at")) auditAt(name, selector, methods, (AnnotationNode) at);
                        }
                    }
                }
            }
        }
    }

    private static List<MethodNode> assertSelector(String mixin, String selector, ClassNode target) {
        String method = selector;
        if (method.startsWith("L")) {
            int end = method.indexOf(';');
            assertTrue(end > 1, mixin + ": invalid selector " + selector);
            assertEquals(target.name, method.substring(1, end), mixin + ": selector owner " + selector);
            method = method.substring(end + 1);
        }
        int paren = method.indexOf('(');
        String name = paren < 0 ? method : method.substring(0, paren);
        String desc = paren < 0 ? null : method.substring(paren);
        boolean wildcard = name.endsWith("*");
        String prefix = wildcard ? name.substring(0, name.length() - 1) : name;
        List<MethodNode> found = target.methods.stream()
                .filter(m -> (wildcard ? m.name.startsWith(prefix) : m.name.equals(prefix))
                        && (desc == null || m.desc.equals(desc))).toList();
        assertFalse(found.isEmpty(), mixin + ": " + target.name + " does not declare selector " + selector);
        return found;
    }

    private static void auditAt(String mixin, String selector, List<MethodNode> methods, AnnotationNode at) {
        String target = string(at, "target");
        if (target.isEmpty()) return;
        String kind = string(at, "value");
        assertTrue(target.startsWith("L") && target.contains(";"), mixin + ": unsupported @At target " + target);
        int end = target.indexOf(';');
        String owner = target.substring(1, end);
        String member = target.substring(end + 1);
        boolean found = false;
        for (MethodNode method : methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && kind.startsWith("INVOKE")
                        && call.owner.equals(owner) && (call.name + call.desc).equals(member)) found = true;
                if (instruction instanceof FieldInsnNode field && kind.equals("FIELD")
                        && field.owner.equals(owner) && (field.name + ":" + field.desc).equals(member)) found = true;
            }
        }
        assertTrue(found, mixin + ": selector " + selector + " has no @At(" + kind + ") target " + target);
    }

    private static void auditAccessor(String mixin, MethodNode handler, AnnotationNode annotation, ClassNode target) {
        String field = string(annotation, "value");
        assertFalse(field.isEmpty(), mixin + ": implicit @Accessor name is unsupported");
        Type[] args = Type.getArgumentTypes(handler.desc);
        Type result = Type.getReturnType(handler.desc);
        String desc;
        if (args.length == 0 && result.getSort() != Type.VOID) desc = result.getDescriptor();
        else if (args.length == 1 && result.getSort() == Type.VOID) desc = args[0].getDescriptor();
        else throw new AssertionError(mixin + ": invalid @Accessor " + handler.name + handler.desc);
        assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(field) && f.desc.equals(desc)),
                mixin + ": " + target.name + " has no @Accessor field " + field + ":" + desc);
    }

    @Test
    void windowFocusInjectionDoesNotCaptureVersionSpecificArguments() throws IOException {
        ClassNode mixin = readClass("dev/krasnokamen/loader/minecraft/mixin/WindowMixin");
        var handler = mixin.methods.stream().filter(m -> m.name.equals("krasnokamen$windowFocus"))
                .findFirst().orElseThrow();
        assertEquals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", handler.desc);
        boolean readsFocus = false;
        for (var instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("com/mojang/blaze3d/platform/Window")
                    && call.name.equals("isFocused") && call.desc.equals("()Z")) readsFocus = true;
        }
        assertTrue(readsFocus, "the callback must use the window's resulting focus state");
    }

    private static ClassNode readClass(String name) throws IOException {
        try (InputStream stream = MixinTargetTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, "missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> all = new ArrayList<>();
        if (method.visibleAnnotations != null) all.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) all.addAll(method.invisibleAnnotations);
        return all;
    }

    private static AnnotationNode findAnnotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String desc) {
        if (visible != null) for (AnnotationNode a : visible) if (a.desc.equals(desc)) return a;
        if (invisible != null) for (AnnotationNode a : invisible) if (a.desc.equals(desc)) return a;
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static List<?> list(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (value == null) return List.of();
        return value instanceof List<?> list ? list : List.of(value);
    }

    private static String string(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        return value == null ? "" : (String) value;
    }
}
