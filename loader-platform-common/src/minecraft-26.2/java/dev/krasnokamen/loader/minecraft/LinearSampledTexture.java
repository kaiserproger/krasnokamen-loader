package dev.krasnokamen.loader.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** A dynamic texture sampled linearly with clamp-to-edge. The sampler type and the field that holds
    it move between Minecraft versions, so only this per-version class names them. */
public class LinearSampledTexture extends DynamicTexture {
    public LinearSampledTexture(Supplier<String> name, int width, int height) {
        super(name, width, height, false);
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }
}
