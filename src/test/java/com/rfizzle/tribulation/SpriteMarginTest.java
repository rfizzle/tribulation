// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the sprite-margin rule on every texture this mod ships from a {@code kind: sprite}
 * glyph spec: the motif must sit inside a 1px fully transparent border, so its {@code ink}
 * outline closes and the sprite reads against any background instead of looking cut off at
 * the slot edge.
 *
 * <p>{@code glyph.py} reports the same violation when a spec is rendered, but the renderer is
 * a local tool — the build only ever sees the PNG. Reading the shipped masters directly is what
 * keeps a clipped silhouette from reaching a release because nobody happened to re-render.
 *
 * <p>The sprite set is derived from the specs rather than hardcoded, because {@code kind:} is
 * what decides which rule a texture owes — a {@code block}, {@code cap}, or {@code ui} texture
 * bleeds to its edges by design and must not be held to this. A sprite added later is covered
 * with no edit here, and {@link #spriteDiscoveryCoversEverySpecThatDeclaresASprite()} is what
 * keeps that promise honest. {@code build.gradle} declares the specs a test input so this class
 * cannot stay {@code UP-TO-DATE} while the art underneath it changes.
 */
class SpriteMarginTest {

    private static final Path SPEC_DIR = Path.of("art/glyphs");

    /**
     * A blunt second opinion on which specs are sprites, deliberately independent of
     * {@link #discoverSprites()}: it ignores directive order, the grid boundary, and every other
     * structural assumption that parser makes, so a divergence between the two is exactly the
     * signal that the parser has started dropping specs.
     */
    private static final Pattern DECLARES_SPRITE = Pattern.compile("(?im)^\\s*kind:\\s*sprite\\b");

    /** A shipped master owed the sprite margin, and the frame size its spec declared (-1 if none). */
    private record Sprite(String spec, Path master, int declaredSize) {
        @Override
        public String toString() {
            return master.toString().replace('\\', '/') + " (from " + spec + ")";
        }
    }

    private static List<Path> specFiles() throws IOException {
        assertTrue(Files.isDirectory(SPEC_DIR), "missing glyph spec dir: " + SPEC_DIR.toAbsolutePath());
        // Walk rather than list, so a spec in a future subdirectory cannot drop out of the sweep.
        try (Stream<Path> files = Files.walk(SPEC_DIR)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".glyph"))
                    .sorted()
                    .toList();
        }
    }

    /** Strips the directive name, then any trailing {@code #} comment. */
    private static String directiveValue(String line) {
        String value = line.substring(line.indexOf(':') + 1);
        int comment = value.indexOf('#');
        if (comment >= 0) {
            value = value.substring(0, comment);
        }
        return value.strip();
    }

    /**
     * Collects the {@code kind: sprite} specs and the masters their {@code ships:} lines name,
     * matching how {@code glyph.py} reads a spec: directives are recognised case-insensitively
     * and anywhere in the file, since there they take precedence over grid-row collection. No
     * legend entry or grid row can begin with one of these keys, so scanning every line is safe.
     */
    private static List<Sprite> discoverSprites() throws IOException {
        List<Sprite> sprites = new ArrayList<>();
        for (Path spec : specFiles()) {
            String kind = null;
            int size = -1;
            List<String> ships = new ArrayList<>();
            for (String raw : Files.readAllLines(spec, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                String low = line.toLowerCase(Locale.ROOT);
                if (low.startsWith("kind:")) {
                    kind = directiveValue(line).toLowerCase(Locale.ROOT);
                } else if (low.startsWith("size:")) {
                    // The renderer takes the first whitespace token and treats size: as optional,
                    // inferring it from the grid — so parse just as loosely and fall back to the
                    // master's own width rather than failing a legal spec.
                    size = parseLeadingInt(directiveValue(line));
                } else if (low.startsWith("ships:")) {
                    ships.add(directiveValue(line));
                }
            }
            if (!"sprite".equals(kind)) continue;

            String name = spec.getFileName().toString();
            for (String ship : ships) {
                // 'ships: <path> [size]' — a size ladder suffixes the tier it renders.
                String[] parts = ship.split("\\s+");
                int shipped = parts.length > 1 ? parseLeadingInt(parts[1]) : size;
                sprites.add(new Sprite(name, Path.of(parts[0]), shipped));
            }
        }
        return sprites;
    }

    /** The leading integer of {@code value}, or -1 when it carries none. */
    private static int parseLeadingInt(String value) {
        String[] parts = value.strip().split("\\s+");
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Border pixels of the {@code size}-tall frame starting at {@code top} that are not fully transparent. */
    private static int opaqueBorderPixels(BufferedImage image, int top, int size) {
        int opaque = 0;
        for (int x = 0; x < size; x++) {
            if ((image.getRGB(x, top) >>> 24) != 0) opaque++;
            if ((image.getRGB(x, top + size - 1) >>> 24) != 0) opaque++;
        }
        for (int y = top + 1; y < top + size - 1; y++) {
            if ((image.getRGB(0, y) >>> 24) != 0) opaque++;
            if ((image.getRGB(size - 1, y) >>> 24) != 0) opaque++;
        }
        return opaque;
    }

    @Test
    void everyShippedSpriteHoldsAOnePixelTransparentMargin() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Sprite sprite : discoverSprites()) {
            if (!Files.isRegularFile(sprite.master())) {
                problems.add(sprite + ": the master its spec ships to does not exist");
                continue;
            }

            // Report a bad master as a problem rather than throwing, so one undecodable file
            // cannot hide the margin violations in every sprite after it.
            BufferedImage image;
            try {
                image = ImageIO.read(sprite.master().toFile());
            } catch (IOException e) {
                problems.add(sprite + ": could not decode the master: " + e);
                continue;
            }
            if (image == null) {
                problems.add(sprite + ": is not an image ImageIO can read");
                continue;
            }

            // A spec may leave size: to be inferred from its grid; the master's own width is
            // then the frame size, which is what the margin is measured against either way.
            int size = sprite.declaredSize() > 0 ? sprite.declaredSize() : image.getWidth();
            if (image.getWidth() != size) {
                problems.add(sprite + ": is " + image.getWidth() + "px wide but its spec declares "
                        + size + "px");
                continue;
            }
            // An animated sprite ships a vertical strip of square frames; a static one is a
            // single frame. Either way each frame owes the margin on its own four sides.
            if (image.getHeight() % size != 0) {
                problems.add(sprite + ": height " + image.getHeight()
                        + "px is not a whole number of " + size + "px frames");
                continue;
            }
            int frames = image.getHeight() / size;
            for (int frame = 0; frame < frames; frame++) {
                int opaque = opaqueBorderPixels(image, frame * size, size);
                if (opaque > 0) {
                    String where = frames > 1 ? " frame " + frame : "";
                    problems.add(sprite + where + ": " + opaque
                            + " border pixels are not transparent — the motif runs into the edge, "
                            + "leaving the ink outline open");
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "sprites must hold a 1px transparent margin on all four sides; edit the .glyph "
                        + "and re-render to the path its ships: line names:\n  "
                        + String.join("\n  ", problems));
    }

    /**
     * The margin check derives its subjects by parsing the specs, so a parsing regression would
     * quietly shrink the sweep and still report green. Cross-check the structured parse against a
     * blunt text scan: every spec that says it is a sprite must have produced one.
     */
    @Test
    void spriteDiscoveryCoversEverySpecThatDeclaresASprite() throws IOException {
        Set<String> discovered = discoverSprites().stream()
                .map(Sprite::spec)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> declared = new TreeSet<>();
        for (Path spec : specFiles()) {
            String text = Files.readString(spec, StandardCharsets.UTF_8);
            if (DECLARES_SPRITE.matcher(text).find()) {
                declared.add(spec.getFileName().toString());
            }
        }

        assertTrue(!declared.isEmpty(), "no spec in " + SPEC_DIR + " declares kind: sprite — "
                + "either the specs moved or this guard is looking in the wrong place");

        Set<String> dropped = new TreeSet<>(declared);
        dropped.removeAll(discovered);
        assertTrue(dropped.isEmpty(),
                "these specs declare kind: sprite but the margin check never saw them, so their "
                        + "shipped masters are unguarded: " + dropped);
    }

    /**
     * Discovery resolves a master from each {@code ships:} line; if that resolution broke, the
     * sweep would run over an empty set of files and still pass. Pin the masters it must reach.
     */
    @Test
    void spriteDiscoveryFindsTheShippedItemSprites() throws IOException {
        Set<String> found = discoverSprites().stream()
                .map(s -> s.master().toString().replace('\\', '/'))
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> expected = new TreeSet<>(Set.of(
                "src/main/resources/assets/tribulation/textures/item/heart_fragment.png",
                "src/main/resources/assets/tribulation/textures/item/shatter_shard.png",
                "src/main/resources/assets/tribulation/textures/item/ascendant_shard.png"));

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(found);
        assertTrue(missing.isEmpty(),
                "sprite discovery missed masters it must cover — the margin check is not "
                        + "guarding them: " + missing + " (found: " + found + ")");
    }
}
