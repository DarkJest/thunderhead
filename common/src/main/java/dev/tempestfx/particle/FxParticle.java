package dev.tempestfx.particle;

import dev.tempestfx.math.Vec3d;

/**
 * One simulated VFX particle.
 */
public final class FxParticle {
    public double x, y, z;
    public double previousX, previousY, previousZ;
    public double velocityX, velocityY, velocityZ;
    public double accelerationX, accelerationY, accelerationZ;

    public float drag = 1;
    public float gravity;
    public float rotation, previousRotation, angularVelocity;
    public float scale, previousScale, startScale, endScale;
    /** Width-to-height ratio of the billboard; anything but 1 stops the particle reading as a ball. */
    public float aspect = 1;
    public float red = 1, green = 1, blue = 1;
    public float startRed = 1, startGreen = 1, startBlue = 1;
    public float endRed = -1, endGreen = -1, endBlue = -1;
    public float alpha, previousAlpha, startAlpha = 1;
    public float emissiveStrength;
    public int lifetime, age;
    public FxParticleMaterial material = FxParticleMaterial.DUST;
    /** Height the particle rests on; only materials that bounce use it. */
    public double floorY = Double.NEGATIVE_INFINITY;

    /** @return {@code true} while the particle is still alive. */
    public boolean tick() {
        previousX = x; previousY = y; previousZ = z;
        previousRotation = rotation;
        previousScale = scale;
        previousAlpha = alpha;

        velocityX = (velocityX + accelerationX) * drag;
        velocityY = (velocityY + accelerationY - gravity) * drag;
        velocityZ = (velocityZ + accelerationZ) * drag;
        x += velocityX; y += velocityY; z += velocityZ;

        if (material.bounces() && y <= floorY && velocityY < 0) {
            y = floorY;
            velocityX *= 0.62; velocityY *= -0.38; velocityZ *= 0.62;
        }
        rotation += angularVelocity;
        age++;

        float life = age / (float) Math.max(1, lifetime);
        scale = startScale + (endScale - startScale) * life;
        alpha = startAlpha * fade(life);
        if (endRed >= 0) {
            red = startRed + (endRed - startRed) * life;
            green = startGreen + (endGreen - startGreen) * life;
            blue = startBlue + (endBlue - startBlue) * life;
        }
        return age < lifetime;
    }

    /** Per-family opacity curve over normalised lifetime. */
    private float fade(float life) {
        float t = Math.min(1, Math.max(0, life));
        return switch (material) {
            case SPARK, MICRO_ARC, EMBER -> (1 - t) * (1 - t);
            case SMOKE, STEAM -> t < 0.18f ? t / 0.18f : 1 - (t - 0.18f) / 0.82f;
            case ASH -> t < 0.08f ? t / 0.08f : 1 - (t - 0.08f) / 0.92f;
            case DEBRIS, WATER -> 1 - t * t * t;
            case DUST -> 1 - t;
        };
    }

    public void setPosition(Vec3d position) { setPosition(position.x(), position.y(), position.z()); }

    public void setPosition(double px, double py, double pz) {
        x = previousX = px; y = previousY = py; z = previousZ = pz;
    }

    public void setVelocity(double vx, double vy, double vz) { velocityX = vx; velocityY = vy; velocityZ = vz; }

    public void setAcceleration(double ax, double ay, double az) {
        accelerationX = ax; accelerationY = ay; accelerationZ = az;
    }

    /** Sets the constant colour and the initial value of the interpolated colour. */
    public void setColor(float r, float g, float b) {
        red = startRed = r; green = startGreen = g; blue = startBlue = b;
        endRed = endGreen = endBlue = -1;
    }

    /** Enables colour interpolation from the current colour to the given target over the lifetime. */
    public void fadeToColor(float r, float g, float b) { endRed = r; endGreen = g; endBlue = b; }

    public void setScale(float from, float to) { startScale = from; endScale = to; scale = previousScale = from; }

    public void setAlpha(float value) { startAlpha = value; alpha = previousAlpha = value; }

    public double interpolatedX(float partialTick) { return previousX + (x - previousX) * partialTick; }
    public double interpolatedY(float partialTick) { return previousY + (y - previousY) * partialTick; }
    public double interpolatedZ(float partialTick) { return previousZ + (z - previousZ) * partialTick; }
    public float interpolatedScale(float partialTick) { return previousScale + (scale - previousScale) * partialTick; }
    public float interpolatedAlpha(float partialTick) { return previousAlpha + (alpha - previousAlpha) * partialTick; }

    public void reset() {
        x = y = z = previousX = previousY = previousZ = 0;
        velocityX = velocityY = velocityZ = 0;
        accelerationX = accelerationY = accelerationZ = 0;
        drag = 1; gravity = 0;
        rotation = previousRotation = angularVelocity = 0;
        scale = previousScale = startScale = endScale = 0;
        aspect = 1;
        red = green = blue = startRed = startGreen = startBlue = 1;
        endRed = endGreen = endBlue = -1;
        alpha = previousAlpha = 0; startAlpha = 1;
        emissiveStrength = 0;
        lifetime = age = 0;
        material = FxParticleMaterial.DUST;
        floorY = Double.NEGATIVE_INFINITY;
    }
}
