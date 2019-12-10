package imagesurf.feature.calculator.histogram;

import java.util.Arrays;
import java.util.stream.Collectors;

class Mask {
    final MaskRow[] rows;
    final int numPixels;

    private Mask(MaskRow[] rows) {
        this.rows = rows;
        this.numPixels = Arrays.stream(rows).mapToInt(row -> row.width).sum();
    }

    static class MaskRow {
        final int offset;
        final int width;

        private MaskRow(int offset, int width) {
            this.offset = offset;
            this.width = width;
        }
    }

    static Mask get(final int radius) {
        final boolean[][] mask = getCircleMask(radius);

        final MaskRow[] rows = Arrays.stream(mask).map((row) -> {
            int offset = -1;
            int width = 0;

            for (int i = 0; i < row.length; i++) {
                if (row[i]) {
                    offset = i;
                    break;
                }
            }

            for (int i = row.length - 1; i >= 0; i--) {
                if (row[i]) {
                    width = i - offset + 1;
                    break;
                }
            }

            return new MaskRow(offset, width);
        }).collect(Collectors.toList()).toArray(new MaskRow[mask.length]);

        return new Mask(rows);
    }

    private static boolean[][] getCircleMask(final int radius) {
        final int diameter = radius * 2 + 1;
        final boolean[][] mask = new boolean[diameter][diameter];

        int d = (5 - radius * 4) / 4;
        int x = 0;
        int y = radius;

        do {
            mask[radius + x][radius + y] = true;
            mask[radius + x][radius - y] = true;
            mask[radius - x][radius + y] = true;
            mask[radius - x][radius - y] = true;
            mask[radius + y][radius + x] = true;
            mask[radius + y][radius - x] = true;
            mask[radius - y][radius + x] = true;
            mask[radius - y][radius - x] = true;
            if (d < 0) {
                d += 2 * x + 1;
            } else {
                d += 2 * (x - y) + 1;
                y--;
            }
            x++;
        } while (x <= y);

        return mask;
    }
}
