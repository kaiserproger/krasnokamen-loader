package dev.krasnokamen.loader.minecraft;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** A tickable sound whose event is fixed. The library method that supplies it was renamed
    (`resolve` in 26.1.2/26.2, `getOrResolve` in 26.3), and an override cannot be named conditionally,
    so only this per-version class names it. */
public abstract class FixedEventSound extends AbstractTickableSoundInstance {
    protected FixedEventSound(SoundEvent event, SoundSource source) {
        super(event, source, SoundInstance.createUnseededRandom());
    }

    protected abstract WeighedSoundEvents event();

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager manager) {
        return event();
    }
}
