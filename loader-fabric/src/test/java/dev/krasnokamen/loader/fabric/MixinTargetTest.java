package dev.krasnokamen.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Check the shipped mixins against the actual Minecraft dependency. */
class MixinTargetTest {
    @Test
    void zombiePoseInjectionTargetsADeclaredGameMethod() throws IOException {
        ClassNode mixin = readClass("dev/krasnokamen/loader/minecraft/mixin/ZombieShieldPoseMixin");
        AnnotationNode mixinAnnotation = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        @SuppressWarnings("unchecked")
        List<Type> targets = (List<Type>) value(mixinAnnotation, "value");
        assertEquals(1, targets.size());
        ClassNode target = readClass(targets.getFirst().getInternalName());
        var handler = mixin.methods.stream().filter(m -> m.name.equals("krasnokamen$shieldPose"))
                .findFirst().orElseThrow();
        AnnotationNode inject = annotation(handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        @SuppressWarnings("unchecked")
        List<String> selectors = (List<String>) value(inject, "method");
        assertFalse(selectors.isEmpty());
        for (String selector : selectors) {
            int descriptor = selector.indexOf('(');
            assertTrue(descriptor > 0, "selector must have an exact descriptor: " + selector);
            String name = selector.substring(0, descriptor);
            String signature = selector.substring(descriptor);
            assertTrue(target.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(signature)),
                    () -> target.name + " does not declare " + selector);
        }
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
                    && call.name.equals("isFocused") && call.desc.equals("()Z")) {
                readsFocus = true;
            }
        }
        assertTrue(readsFocus, "the callback must use the window's resulting focus state");
        ClassNode window = readClass("com/mojang/blaze3d/platform/Window");
        assertEquals(1, window.methods.stream().filter(m -> m.name.equals("onFocus")).count());
        assertTrue(window.methods.stream().anyMatch(m -> m.name.equals("isFocused") && m.desc.equals("()Z")));
    }

    private static ClassNode readClass(String name) throws IOException {
        try (InputStream stream = MixinTargetTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        assertNotNull(annotations, descriptor);
        return annotations.stream().filter(a -> a.desc.equals(descriptor)).findFirst().orElseThrow();
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing " + key + " in " + annotation.desc);
    }
}
