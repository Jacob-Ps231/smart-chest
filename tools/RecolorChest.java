import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Provisional Smart Chest textures (SPEC §9 q.3): a recoloured copy of the vanilla
 * oak chest. The grey latch turns copper, like the ingot in the recipe, and the wood
 * is darkened so a Smart Chest never reads as a vanilla chest next to one.
 *
 * Usage, from the project root:
 *   java tools/RecolorChest.java C:/gradle-home/caches/fabric-loom/26.3/minecraft-merged.jar
 */
public class RecolorChest {
	private static final String SOURCE = "assets/minecraft/textures/entity/chest/";
	private static final File TARGET = new File("src/main/resources/assets/smartchest/textures/entity/chest");
	private static final String WOOD = "oak";

	public static void main(String[] args) throws Exception {
		TARGET.mkdirs();
		try (ZipFile jar = new ZipFile(args[0])) {
			for (String part : new String[] {"", "_left", "_right"}) {
				BufferedImage image;
				try (InputStream in = jar.getInputStream(jar.getEntry(SOURCE + "normal" + part + ".png"))) {
					image = ImageIO.read(in);
				}
				BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						out.setRGB(x, y, recolor(image.getRGB(x, y)));
					}
				}
				File file = new File(TARGET, WOOD + part + ".png");
				ImageIO.write(out, "png", file);
				System.out.println("Wrote " + file);
			}
		}
	}

	private static int recolor(int argb) {
		int alpha = argb >>> 24;
		if (alpha == 0) {
			return argb;
		}
		float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
		int rgb;
		if (hsb[1] < 0.2F) {
			// The latch is the only grey on the texture.
			rgb = Color.HSBtoRGB(0.06F, 0.7F, Math.min(1.0F, hsb[2] * 1.05F));
		} else {
			rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2] * 0.7F);
		}
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}
}
