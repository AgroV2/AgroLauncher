package by.agro.launcher.ui.background;

import java.awt.image.BufferedImage;

public final class GaussianBlur {

    private GaussianBlur() {
    }

    public static BufferedImage blur(BufferedImage source, int radius) {
        if (source == null || radius < 1) {
            return source;
        }
        int clampedRadius = Math.min(60, radius);
        float[] kernel = buildKernel(clampedRadius);

        int width = source.getWidth();
        int height = source.getHeight();

        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        int[] temp = new int[pixels.length];

        blurPass(pixels, temp, width, height, kernel, clampedRadius);
        blurPass(temp, pixels, height, width, kernel, clampedRadius);

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    private static void blurPass(int[] source, int[] target, int width, int height,
                                 float[] kernel, int radius) {
        int index = 0;
        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                float red = 0;
                float green = 0;
                float blue = 0;

                for (int k = -radius; k <= radius; k++) {
                    int sampleX = x + k;
                    if (sampleX < 0) {
                        sampleX = -sampleX;
                    } else if (sampleX >= width) {
                        sampleX = 2 * width - sampleX - 2;
                    }
                    if (sampleX < 0 || sampleX >= width) {
                        sampleX = x;
                    }

                    int pixel = source[rowStart + sampleX];
                    float weight = kernel[k + radius];
                    red += ((pixel >> 16) & 0xFF) * weight;
                    green += ((pixel >> 8) & 0xFF) * weight;
                    blue += (pixel & 0xFF) * weight;
                }

                int r = clamp((int) (red + 0.5f));
                int g = clamp((int) (green + 0.5f));
                int b = clamp((int) (blue + 0.5f));

                target[index++] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        transposeInPlace(target, width, height);
    }

    private static void transposeInPlace(int[] data, int width, int height) {
        int[] copy = new int[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[x * height + y] = copy[y * width + x];
            }
        }
    }

    private static float[] buildKernel(int radius) {
        int size = radius * 2 + 1;
        float[] kernel = new float[size];
        float sigma = radius / 2.4f;
        float twoSigmaSquare = 2 * sigma * sigma;
        float sum = 0;

        for (int i = -radius; i <= radius; i++) {
            float value = (float) Math.exp(-(i * i) / twoSigmaSquare);
            kernel[i + radius] = value;
            sum += value;
        }
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }
}
