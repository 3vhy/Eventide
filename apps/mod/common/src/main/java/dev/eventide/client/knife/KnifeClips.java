package dev.eventide.client.knife;

/**
 * Each knife's draw and inspect, for the knife held in Minecraft's own arm. Signature ones for the knives
 * known for them: Eclipse spins up round its ring, Wraith flips its handles open, Vesper flicks its blade out,
 * Riptide and Nocturne flip end over end into the fist, Lumen ignites. Inspects bring the arm in and turn the
 * blade's flat towards you, then show the knife's own trick. Values are in the units {@link Clip} describes.
 */
final class KnifeClips {
	/** The inspect's opening pose: the arm brought in and up, the wrist turned so the blade's flat faces you. */
	private static final float[] PRESENT = {Clip.X, -0.16f, Clip.Y, 0.12f, Clip.Z, 0.03f, Clip.YAW, 14, Clip.PITCH, 8, Clip.ROLL, 16, Clip.K_ROLL, 24};

	private KnifeClips() {
	}

	static Clip draw(KnifeModel model) {
		return switch (model) {
			case ECLIPSE -> Clip.of("draw", 900)
					.key(0, Clip.Y, -0.55f, Clip.PITCH, -30, Clip.K_ROLL, -540)
					.key(0.62f, Clip.Y, 0.02f, Clip.PITCH, 3, Clip.K_ROLL, -12)
					.key(1);
			case WRAITH -> Clip.of("draw", 1100)
					.key(0, Clip.Y, -0.55f, Clip.OPEN, 0)
					.key(0.3f, Clip.Y, 0.0f, Clip.OPEN, 0)
					.key(0.52f, Clip.OPEN, 0.45f, Clip.HANDLE_B, 140, Clip.K_ROLL, -180)
					.key(0.78f, Clip.OPEN, 1, Clip.HANDLE_B, 0, Clip.K_ROLL, -360)
					.key(1, Clip.K_ROLL, -360);
			case VESPER -> Clip.of("draw", 780)
					.key(0, Clip.Y, -0.55f, Clip.OPEN, 0, Clip.ROLL, 20)
					.key(0.34f, Clip.Y, -0.02f, Clip.OPEN, 0, Clip.ROLL, 12, Clip.PITCH, 8)
					.key(0.5f, Clip.Y, 0.02f, Clip.OPEN, 1.08f, Clip.ROLL, -6, Clip.PITCH, -5)
					.key(0.66f, Clip.OPEN, 0.97f, Clip.ROLL, 2)
					.key(1);
			case RIPTIDE, NOCTURNE -> Clip.of("draw", 880)
					.key(0, Clip.Y, -0.55f, Clip.X, 0.12f, Clip.K_ROLL, 360, Clip.PITCH, -20)
					.key(0.6f, Clip.Y, 0.03f, Clip.X, -0.01f, Clip.K_ROLL, 0, Clip.PITCH, 4)
					.key(1);
			case HALO -> Clip.of("draw", 680)
					.key(0, Clip.Y, -0.55f, Clip.PITCH, -40)
					.key(0.6f, Clip.Y, 0.02f, Clip.PITCH, 6)
					.key(1);
			case LUMEN -> Clip.of("draw", 900)
					.key(0, Clip.Y, -0.55f, Clip.OPEN, 0)
					.key(0.38f, Clip.Y, 0.0f, Clip.OPEN, 0)
					.key(0.62f, Clip.OPEN, 1.08f)
					.key(1);
			case SERAPH -> Clip.of("draw", 950)
					.key(0, Clip.X, -0.2f, Clip.Y, -0.5f, Clip.YAW, -30, Clip.ROLL, 30)
					.key(0.6f, Clip.X, 0.02f, Clip.YAW, 4, Clip.ROLL, -4)
					.key(1);
			case REAPER -> Clip.of("draw", 1000)
					.key(0, Clip.X, 0.25f, Clip.Y, -0.55f, Clip.K_ROLL, 360)
					.key(0.68f, Clip.Y, 0.03f, Clip.K_ROLL, 0, Clip.X, 0)
					.key(1);
			default -> Clip.of("draw", 800)
					.key(0, Clip.X, 0.2f, Clip.Y, -0.55f, Clip.K_YAW, 360, Clip.ROLL, -30)
					.key(0.68f, Clip.X, -0.01f, Clip.Y, 0.03f, Clip.K_YAW, 0, Clip.ROLL, 4)
					.key(1);
		};
	}

	static Clip inspect(KnifeModel model) {
		return switch (model) {
			case ECLIPSE -> Clip.of("inspect", 3200)
					.key(0)
					.key(0.14f, PRESENT)
					.then(0.3f, Clip.K_YAW, 160)
					.then(0.42f, Clip.K_YAW, 0)
					.then(0.66f, Clip.K_ROLL, -720, Clip.ROLL, 30)
					.then(0.8f, Clip.K_ROLL, -720, Clip.ROLL, 36, Clip.YAW, 26)
					.key(1, Clip.K_ROLL, -720);
			case WRAITH -> Clip.of("inspect", 3600)
					.key(0)
					.key(0.1f, PRESENT)
					.then(0.25f, Clip.OPEN, 0.5f, Clip.HANDLE_A, -150, Clip.K_ROLL, 180)
					.then(0.38f, Clip.OPEN, 0, Clip.HANDLE_A, 0, Clip.K_ROLL, 360)
					.then(0.52f, Clip.OPEN, 0.5f, Clip.HANDLE_B, 150, Clip.K_ROLL, 540)
					.then(0.66f, Clip.OPEN, 1, Clip.HANDLE_B, 0, Clip.K_ROLL, 720)
					.then(0.82f, Clip.K_YAW, 180)
					.key(1, Clip.K_ROLL, 720, Clip.K_YAW, 360);
			case VESPER -> Clip.of("inspect", 3100)
					.key(0)
					.key(0.13f, PRESENT)
					.then(0.32f, Clip.K_YAW, 180)
					.then(0.46f, Clip.OPEN, 0.15f)
					.then(0.58f, Clip.OPEN, 1.06f)
					.then(0.64f, Clip.OPEN, 1)
					.then(0.84f, Clip.K_YAW, 360)
					.key(1, Clip.K_YAW, 360);
			case RIPTIDE -> Clip.of("inspect", 3100)
					.key(0)
					.key(0.14f, PRESENT)
					.then(0.34f, Clip.K_YAW, 180)
					.then(0.5f, Clip.SLIDE, 4, Clip.K_PITCH, -180)
					.then(0.62f, Clip.SLIDE, 0, Clip.K_PITCH, -360)
					.then(0.85f, Clip.K_YAW, 360)
					.key(1, Clip.K_YAW, 360, Clip.K_PITCH, -360);
			case NOCTURNE, HALO -> Clip.of("inspect", 3000)
					.key(0)
					.key(0.12f, PRESENT)
					.then(0.32f, Clip.K_ROLL, 360, Clip.SLIDE, 2)
					.then(0.5f, Clip.K_ROLL, 720, Clip.SLIDE, 0)
					.then(0.7f, Clip.K_YAW, 180)
					.key(1, Clip.K_ROLL, 720, Clip.K_YAW, 360);
			case LUMEN -> Clip.of("inspect", 3200)
					.key(0)
					.key(0.12f, PRESENT)
					.then(0.4f, Clip.K_ROLL, -360, Clip.OPEN, 0.85f)
					.then(0.62f, Clip.K_ROLL, -720, Clip.OPEN, 1.08f)
					.then(0.8f, Clip.K_YAW, 180, Clip.OPEN, 1)
					.key(1, Clip.K_ROLL, -720, Clip.K_YAW, 360);
			case SERAPH -> Clip.of("inspect", 3600)
					.key(0)
					.key(0.16f, PRESENT)
					.then(0.42f, Clip.X, -0.12f, Clip.ROLL, 34)
					.then(0.58f, Clip.K_YAW, 180)
					.then(0.74f, Clip.X, -0.04f)
					.key(1, Clip.K_YAW, 360);
			case REAPER -> Clip.of("inspect", 3000)
					.key(0)
					.key(0.14f, PRESENT)
					.then(0.45f, Clip.K_ROLL, 360)
					.then(0.72f, Clip.K_ROLL, 720, Clip.YAW, 26)
					.key(1, Clip.K_ROLL, 720);
			default -> Clip.of("inspect", 3000)
					.key(0)
					.key(0.15f, PRESENT)
					.then(0.35f, Clip.ROLL, 26)
					.then(0.55f, Clip.K_YAW, 180)
					.then(0.72f, Clip.ROLL, 36, Clip.K_YAW, 360)
					.key(1, Clip.K_YAW, 360);
		};
	}
}
