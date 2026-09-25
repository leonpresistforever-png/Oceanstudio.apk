# OceanStudio 1.2.3 Release Notes

## Production Engineering & Architecture Overhaul

### 1. Genuine CC0 Blue Sky & 100x100 Baseplate
- Embedded authentic Creative Commons CC0 sky and cloud texture (`sky.jpg`) directly acquired from upstream Wikimedia Commons.
- Extended baseplate to 100x100 meters with dual-density major (5m) and minor (1m) grid lines.
- Horizon atmospheric gradient smoothly connects the 3D floor to the open-air cloudscape.

### 2. Mobile 3D Touch Interaction & Real Raycasting
- Direct touch dragging: touching any selected object unprojects the touch point onto the horizontal 3D plane at object height, moving the object under the user's finger with zero lag.
- Axis gizmo constraint handles: dedicated Red (X), Green (Y), and Blue (Z) arrows allow constrained translation along single axes.
- Fluid camera controls: 1-finger background orbit, 2-finger pan and pinch-to-zoom.

### 3. Integrated Physics Engine
- 60 FPS real-time physics simulation loop with gravity (-9.8 m/s²), baseplate collision detection, velocity integration, and surface friction.
- Static and Dynamic rigid body modes toggleable per object.

### 4. Interactive Inspector & Streamlined Mobile Toolbar
- Replaced non-functional mock cards with an interactive on-screen toolbar (Move, Rotate, Scale, Add, Duplicate, Delete, Inspector, Import, Export).
- Real Inspector drawer with direct step adjusters for X/Y/Z positions, rotation angles, scale factors, physics modes, and material color tinting chips.

### 5. Verified Native Upstream Distro & Glibc Fixes
- Hardlink unpacking permission fix in proot-distro and ocean-distro via PRoot link2symlink.
- Addition of genuine glibc meta-package and active glibc providers into the signed APT catalogue.
