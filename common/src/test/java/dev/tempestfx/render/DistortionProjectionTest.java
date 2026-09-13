package dev.tempestfx.render;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistortionProjectionTest {
    @Test void forwardPositiveWorldZIsVisibleAfterCameraRotation() {
        var cameraRotation = new Matrix4f().rotationY((float)Math.PI);
        var pose = new Matrix4f().translation(-152.5f,-76,-173.5f);
        var projection = new Matrix4f().perspective((float)Math.toRadians(70),16f/9f,.05f,1000);
        var point = new Vector4f(); var uv = new float[2];
        ScreenProjection.toView(cameraRotation,pose,point,152.5f,71,197.5f);
        assertTrue(point.z<0, "impact ahead must not be rejected as behind the camera");
        assertTrue(ScreenProjection.toScreen(projection,new Vector4f(),point.x,point.y,point.z,point.w,uv));
        assertEquals(.5f,uv[0],1e-5);
    }
}
