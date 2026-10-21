package io.github.thewombraider.pipestv;

import java.util.Random;

/**
 * The 16 materials the original gives its pipes: a subset of the classic OpenGL "teapots" material table (SGI's
 * teapots.c sample), each with its own ambient, diffuse and specular color and shininess. See NOTICE.
 */
final class Materials {
    private Materials() {}

    static final String[] NAMES = {
        "emerald", "jade", "pearl", "ruby", "turquoise", "brass", "bronze", "copper",
        "gold", "silver", "cyan plastic", "white plastic", "yellow plastic", "cyan rubber", "green rubber", "white rubber",
    };

    /** Per material: ambient rgb, diffuse rgb, specular rgb, shininess as a fraction of 128. */
    private static final float[][] DATA = {
        {.0215f, .1745f, .0215f, .07568f, .61424f, .07568f, .633f, .727811f, .633f, .6f},
        {.135f, .2225f, .1575f, .54f, .89f, .63f, .316228f, .316228f, .316228f, .1f},
        {.25f, .20725f, .20725f, 1f, .829f, .829f, .296648f, .296648f, .296648f, .088f},
        {.1745f, .01175f, .01175f, .61424f, .04136f, .04136f, .727811f, .626959f, .626959f, .6f},
        {.1f, .18725f, .1745f, .396f, .74151f, .69102f, .297254f, .30829f, .306678f, .1f},
        {.329412f, .223529f, .027451f, .780392f, .568627f, .113725f, .992157f, .941176f, .807843f, .21794872f},
        {.2125f, .1275f, .054f, .714f, .4284f, .18144f, .393548f, .271906f, .166721f, .2f},
        {.19125f, .0735f, .0225f, .7038f, .27048f, .0828f, .256777f, .137622f, .086014f, .1f},
        {.24725f, .1995f, .0745f, .75164f, .60648f, .22648f, .628281f, .555802f, .366065f, .4f},
        {.19225f, .19225f, .19225f, .50754f, .50754f, .50754f, .508273f, .508273f, .508273f, .4f},
        {0f, .1f, .06f, 0f, .50980392f, .50980392f, .50196078f, .50196078f, .50196078f, .25f},
        {0f, 0f, 0f, .55f, .55f, .55f, .70f, .70f, .70f, .25f},
        {0f, 0f, 0f, .5f, .5f, 0f, .60f, .60f, .50f, .25f},
        {0f, .05f, .05f, .4f, .5f, .5f, .04f, .7f, .7f, .078125f},
        {0f, .05f, 0f, .4f, .5f, .4f, .04f, .7f, .04f, .078125f},
        {.05f, .05f, .05f, .5f, .5f, .5f, .7f, .7f, .7f, .078125f},
    };

    static final int COUNT = DATA.length;

    static int randomPipeMaterial(Random rng) {
        return rng.nextInt(COUNT);
    }

    /** {ambient rgb, diffuse rgb, specular rgb, shininess exponent} for a material. */
    static float[] get(int index) {
        float[] d = DATA[index];
        return new float[] {d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9] * 128f};
    }
}
