package mcheli.dependent.client;

import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Render state for every MCHeli vehicle renderer. 26.x splits entity rendering into an EXTRACT phase (copy what the
 * frame needs off the entity) and a SUBMIT phase (queue geometry). The vehicle draw reads dozens of per-part animation
 * values (rotor/blade angles, gear, turret ring, every weapon operator's aim, missile racks…), so instead of mirroring
 * all of them this keeps the entity + partial tick and the submit phase queues ONE custom-geometry callback that runs
 * the existing draw code on the render thread, later in the same frame.
 */
public class MchVehicleRenderState extends EntityRenderState {
    public AbstractMchVehicle entity;
    public float partialTick;
    /** Interpolated body yaw (what the 1.21.1 {@code render(entity, entityYaw, ...)} received). */
    public float yaw;
}
