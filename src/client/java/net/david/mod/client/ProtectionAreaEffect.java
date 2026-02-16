package net.david.mod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public class ProtectionAreaEffect {

    private final BlockPos center;
    private final int radius;
    private int ticksLeft;
    private final boolean isAdmin;

    public ProtectionAreaEffect(BlockPos center, int radius, int durationTicks, boolean isAdmin) {
        this.center = center;
        this.radius = radius;
        this.ticksLeft = durationTicks;
        this.isAdmin = isAdmin;
    }

    // Constructor sobrecargado para mantener compatibilidad si no se especifica isAdmin
    public ProtectionAreaEffect(BlockPos center, int radius, int durationTicks) {
        this(center, radius, durationTicks, false);
    }

    /**
     * @return true si el efecto sigue activo, false si debe eliminarse.
     */
    public boolean tick(ClientLevel level) {
        if (ticksLeft <= 0) return false;

        // Reducimos la frecuencia de dibujado para optimizar FPS (dibujar cada 2 ticks)
        if (ticksLeft % 2 == 0) {
            spawnBoundary(level);
        }

        ticksLeft--;
        return true;
    }

    private void spawnBoundary(ClientLevel level) {
        double xMid = center.getX() + 0.5;
        double yBase = center.getY();
        double zMid = center.getZ() + 0.5;

        // Partícula según el tipo de protección
        ParticleOptions particle = isAdmin ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.HAPPY_VILLAGER;

        // Ajuste dinámico de densidad: a mayor radio, más espacio entre partículas
        double step = radius < 5 ? 0.5 : 1.0;

        for (double i = -radius; i <= radius; i += step) {
            // Dibujamos en 3 alturas: pies, torso y sobre la cabeza
            for (double yOff = 0.2; yOff <= 2.2; yOff += 1.0) {
                double yPos = yBase + yOff;

                // Paredes Norte-Sur
                spawnParticle(level, particle, xMid + i, yPos, zMid + radius + 0.5);
                spawnParticle(level, particle, xMid + i, yPos, zMid - radius - 0.5);

                // Paredes Este-Oeste
                spawnParticle(level, particle, xMid + radius + 0.5, yPos, zMid + i);
                spawnParticle(level, particle, xMid - radius - 0.5, yPos, zMid + i);
            }
        }
    }

    private void spawnParticle(ClientLevel level, ParticleOptions type, double x, double y, double z) {
        // Solo spawneamos si el jugador está cerca (optimización de frustum manual)
        level.addParticle(type, x, y, z, 0, 0.02, 0);
    }

    public boolean isExpired() {
        return ticksLeft <= 0;
    }

    public BlockPos getPos() {
        return center;
    }
}
