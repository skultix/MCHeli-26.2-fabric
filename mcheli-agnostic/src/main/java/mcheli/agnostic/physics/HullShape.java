package mcheli.agnostic.physics;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.model.ModelFace;
import mcheli.agnostic.model.ModelGroup;
import mcheli.agnostic.model.ModelVertex;
import mcheli.agnostic.model.MqoModel;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;

/**
 * A vehicle's hit hull, built from its MODEL: the surface of every group the renderer draws ({@code $body} + the
 * config-declared parts), minus spinning rotor/propeller blades, voxelized at {@code cell} resolution and greedily
 * merged into boxes. Coordinates are the vehicle's local render space: model X/Z, and Y lifted by {@code -minY} so the
 * lowest vertex sits at 0 (the renderer's lift). The physics core (config {@code bodyWidth×bodyHeight}) and the config
 * part boxes (armour / weak-point factors) are separate; this only answers "does a shot hit the vehicle, and where".
 *
 * <p>Pure geometry — the platform layer rotates a world ray into this frame before {@link #clip}.
 */
public final class HullShape {
    /** Local boxes {@code [minX, minY, minZ, maxX, maxY, maxZ]}. */
    private final double[][] boxes;
    /** Local bounds of all boxes (quick reject). */
    private final double[] bounds;

    private HullShape(double[][] boxes) {
        this.boxes = boxes;
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] x : boxes) {
            for (int i = 0; i < 3; i++) {
                b[i] = Math.min(b[i], x[i]);
                b[i + 3] = Math.max(b[i + 3], x[i + 3]);
            }
        }
        this.bounds = b;
    }

    public int boxCount() {
        return this.boxes.length;
    }

    /** A copy of the local boxes (diagnostics / tests). */
    public double[][] boxes() {
        double[][] out = new double[this.boxes.length][];
        for (int i = 0; i < out.length; i++) {
            out[i] = this.boxes[i].clone();
        }
        return out;
    }

    /**
     * Fraction {@code t} in [0,1] along the local segment {@code from -> to} where it first enters the hull, or NaN when
     * it misses. A segment that STARTS inside a box enters at t = 0.
     */
    public double clip(double fx, double fy, double fz, double tx, double ty, double tz) {
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;
        if (Double.isNaN(slab(this.bounds, fx, fy, fz, dx, dy, dz))) {
            return Double.NaN;
        }
        double best = Double.NaN;
        for (double[] b : this.boxes) {
            double t = slab(b, fx, fy, fz, dx, dy, dz);
            if (!Double.isNaN(t) && (Double.isNaN(best) || t < best)) {
                best = t;
            }
        }
        return best;
    }

    /** Ray/box slab test over t in [0,1]; entry t (0 when starting inside) or NaN. */
    private static double slab(double[] b, double fx, double fy, double fz, double dx, double dy, double dz) {
        double t0 = 0.0;
        double t1 = 1.0;
        double[] o = {fx, fy, fz};
        double[] d = {dx, dy, dz};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0e-12) {
                if (o[i] < b[i] || o[i] > b[i + 3]) {
                    return Double.NaN;
                }
                continue;
            }
            double ta = (b[i] - o[i]) / d[i];
            double tb = (b[i + 3] - o[i]) / d[i];
            if (ta > tb) {
                double s = ta;
                ta = tb;
                tb = s;
            }
            t0 = Math.max(t0, ta);
            t1 = Math.min(t1, tb);
            if (t0 > t1) {
                return Double.NaN;
            }
        }
        return t0;
    }

    // ---- building ----

    /** Build the hull for {@code info}'s model; {@code cell} is the voxel edge (0.5 = half a block). */
    public static HullShape build(MchModel model, MCH_AircraftInfo info, double cell) {
        Set<String> drawn = drawnPartsLower(info);
        Set<String> blades = bladePartsLower(info);
        List<ModelGroup> groups = model.groups();
        boolean hasBody = false;
        for (ModelGroup g : groups) {
            if (g != null && g.name.equalsIgnoreCase("$body")) {
                hasBody = true;
                break;
            }
        }
        boolean mqo = model instanceof MqoModel;
        double lift = -model.minY;
        int nx = (int) Math.ceil((model.maxX - model.minX) / cell) + 1;
        int ny = (int) Math.ceil((model.maxY - model.minY) / cell) + 1;
        int nz = (int) Math.ceil((model.maxZ - model.minZ) / cell) + 1;
        BitSet filled = new BitSet(nx * ny * nz);

        // Walk the groups with the renderer's span rule: an MQO "$part" owns the following non-$ groups.
        String spanOwner = null;
        for (ModelGroup g : groups) {
            if (g == null) {
                continue;
            }
            String name = g.name.toLowerCase(Locale.ROOT);
            if (name.startsWith("$") || !mqo) {
                spanOwner = name;
            }
            String owner = spanOwner != null ? spanOwner : name;
            boolean visible = hasBody ? (owner.equals("$body") || drawn.contains(owner)) : true;
            if (!visible || blades.contains(owner)) {
                continue;
            }
            for (ModelFace f : g.faces) {
                rasterize(f, model, cell, nx, ny, nz, filled);
            }
        }

        List<double[]> boxes = merge(filled, nx, ny, nz, cell, model.minX, lift + model.minY, model.minZ);
        return new HullShape(boxes.toArray(new double[0][]));
    }

    /** Mark every voxel the face's surface passes through (sampled at under half a cell; triangle fan for quads). */
    private static void rasterize(ModelFace f, MchModel m, double cell, int nx, int ny, int nz, BitSet filled) {
        ModelVertex[] v = f.vertices;
        if (v == null || v.length < 3) {
            return;
        }
        double step = cell * 0.45;
        for (int k = 1; k + 1 < v.length; k++) {
            ModelVertex a = v[0];
            ModelVertex b = v[k];
            ModelVertex c = v[k + 1];
            double lab = dist(a, b);
            double lac = dist(a, c);
            int nu = Math.max(1, (int) Math.ceil(lab / step));
            int nv = Math.max(1, (int) Math.ceil(lac / step));
            for (int i = 0; i <= nu; i++) {
                double u = (double) i / nu;
                for (int j = 0; j <= nv; j++) {
                    double w = (double) j / nv;
                    if (u + w > 1.0) {
                        continue;
                    }
                    double x = a.x + u * (b.x - a.x) + w * (c.x - a.x);
                    double y = a.y + u * (b.y - a.y) + w * (c.y - a.y);
                    double z = a.z + u * (b.z - a.z) + w * (c.z - a.z);
                    int ix = clamp((int) Math.floor((x - m.minX) / cell), nx);
                    int iy = clamp((int) Math.floor((y - m.minY) / cell), ny);
                    int iz = clamp((int) Math.floor((z - m.minZ) / cell), nz);
                    filled.set((iy * nz + iz) * nx + ix);
                }
            }
        }
    }

    /** Greedy merge of the voxel set into boxes (x runs, then z rows, then y layers). */
    private static List<double[]> merge(BitSet filled, int nx, int ny, int nz, double cell,
                                        double ox, double oy, double oz) {
        BitSet used = new BitSet(filled.size());
        List<double[]> out = new ArrayList<>();
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    int idx = (y * nz + z) * nx + x;
                    if (!filled.get(idx) || used.get(idx)) {
                        continue;
                    }
                    int x1 = x;
                    while (x1 + 1 < nx && free(filled, used, x1 + 1, y, z, nx, nz)) {
                        x1++;
                    }
                    int z1 = z;
                    while (z1 + 1 < nz && rowFree(filled, used, x, x1, y, z1 + 1, nx, nz)) {
                        z1++;
                    }
                    int y1 = y;
                    while (y1 + 1 < ny && layerFree(filled, used, x, x1, z, z1, y1 + 1, nx, nz)) {
                        y1++;
                    }
                    for (int yy = y; yy <= y1; yy++) {
                        for (int zz = z; zz <= z1; zz++) {
                            for (int xx = x; xx <= x1; xx++) {
                                used.set((yy * nz + zz) * nx + xx);
                            }
                        }
                    }
                    out.add(new double[] {ox + x * cell, oy + y * cell, oz + z * cell,
                        ox + (x1 + 1) * cell, oy + (y1 + 1) * cell, oz + (z1 + 1) * cell});
                }
            }
        }
        return out;
    }

    private static boolean free(BitSet f, BitSet u, int x, int y, int z, int nx, int nz) {
        int i = (y * nz + z) * nx + x;
        return f.get(i) && !u.get(i);
    }

    private static boolean rowFree(BitSet f, BitSet u, int x0, int x1, int y, int z, int nx, int nz) {
        for (int x = x0; x <= x1; x++) {
            if (!free(f, u, x, y, z, nx, nz)) {
                return false;
            }
        }
        return true;
    }

    private static boolean layerFree(BitSet f, BitSet u, int x0, int x1, int z0, int z1, int y, int nx, int nz) {
        for (int z = z0; z <= z1; z++) {
            if (!rowFree(f, u, x0, x1, y, z, nx, nz)) {
                return false;
            }
        }
        return true;
    }

    private static int clamp(int i, int n) {
        return i < 0 ? 0 : (i >= n ? n - 1 : i);
    }

    private static double dist(ModelVertex a, ModelVertex b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- which model groups are drawn ----

    /**
     * Lower-cased {@code $}-group names the config DECLARES as drawable parts (every {@code DrawnPart} list across the
     * category infos). The reference draws exactly {@code $body} + these; any other {@code $}-group (hidden weapon
     * variants, camera markers, dummies) is never drawn. Shared by the renderer and the hit hull.
     */
    public static Set<String> drawnPartsLower(MCH_AircraftInfo info) {
        Set<String> s = new HashSet<>();
        if (info == null) {
            return s;
        }
        addDrawn(s, info.partWeapon);
        if (info.partWeapon != null) {
            for (MCH_AircraftInfo.PartWeapon w : info.partWeapon) {
                if (w != null && w.child != null) {
                    addDrawn(s, w.child);
                }
            }
        }
        addDrawn(s, info.partWeaponBay);
        addDrawn(s, info.hatchList);
        addDrawn(s, info.lightHatchList);
        addDrawn(s, info.canopyList);
        addDrawn(s, info.landingGear);
        addDrawn(s, info.partThrottle);
        addDrawn(s, info.partRotPart);
        addDrawn(s, info.cameraList);
        addDrawn(s, info.partWheel);
        addDrawn(s, info.partSteeringWheel);
        addDrawn(s, info.partCrawlerTrack);
        addDrawn(s, info.partTrackRoller);
        if (info instanceof MCH_HeliInfo hi) {
            addDrawn(s, hi.rotorList);
        }
        if (info instanceof MCP_PlaneInfo pi) {
            addDrawn(s, pi.nozzles);
            addDrawn(s, pi.rotorList);
            for (MCP_PlaneInfo.Rotor r : pi.rotorList) {
                if (r != null) {
                    addDrawn(s, r.blades);
                }
            }
            addDrawn(s, pi.wingList);
            for (MCP_PlaneInfo.Wing w : pi.wingList) {
                if (w != null && w.pylonList != null) {
                    addDrawn(s, w.pylonList);
                }
            }
        }
        if (info instanceof MCH_VehicleInfo vi) {
            collectVParts(vi.partList, s);
        }
        return s;
    }

    /** Spinning rotor / propeller BLADE groups: a helicopter's rotors, a plane's propeller blades (its hub/nacelle stays). */
    public static Set<String> bladePartsLower(MCH_AircraftInfo info) {
        Set<String> s = new HashSet<>();
        if (info instanceof MCH_HeliInfo hi) {
            addDrawn(s, hi.rotorList);
        }
        if (info instanceof MCP_PlaneInfo pi) {
            for (MCP_PlaneInfo.Rotor r : pi.rotorList) {
                if (r != null) {
                    addDrawn(s, r.blades);
                }
            }
        }
        return s;
    }

    private static void addDrawn(Set<String> out, List<? extends MCH_AircraftInfo.DrawnPart> parts) {
        if (parts != null) {
            for (MCH_AircraftInfo.DrawnPart p : parts) {
                if (p != null) {
                    out.add(("$" + p.modelName).toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private static void collectVParts(List<MCH_VehicleInfo.VPart> parts, Set<String> out) {
        if (parts != null) {
            for (MCH_VehicleInfo.VPart p : parts) {
                if (p != null) {
                    out.add(("$" + p.modelName).toLowerCase(Locale.ROOT));
                    collectVParts(p.child, out);
                }
            }
        }
    }
}
