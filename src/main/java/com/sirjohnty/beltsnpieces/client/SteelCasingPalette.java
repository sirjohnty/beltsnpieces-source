package com.sirjohnty.beltsnpieces.client;

/** Recolour trim without replacing the belt's panel, border, or shading pattern. */
final class SteelCasingPalette {
    private int darkest,lightest;
    SteelCasingPalette(int[] material) {
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(int color:material) {
            int r=red(color),g=green(color),b=blue(color);
            // TFMG's metal is neutral/cool; exclude its brown wooden panel pixels.
            if((color>>>24)==0 || r>g*1.08 || r>b*1.08) continue;
            double shade=brightness(color);
            if(shade<low) { low=shade;darkest=color; }
            if(shade>high) { high=shade;lightest=color; }
        }
        // Resource packs may contain only warm colours or fully transparent pixels.
        // Use neutral steel trim rather than failing the entire atlas reload.
        if(low==Double.POSITIVE_INFINITY) {
            darkest=0xff404040;lightest=0xffc0c0c0;
        }
    }
    int recolor(int brass,int andesite) {
        int r=red(andesite),g=green(andesite),b=blue(andesite);
        int maximum=Math.max(r,Math.max(g,b)),minimum=Math.min(r,Math.min(g,b));
        // The paired andesite layout identifies metal even where brass has brown edge shading.
        if(maximum-minimum>maximum*.18) return brass;
        double shade=Math.clamp(brightness(andesite)/.72,0,1);
        int rr=channel(red(darkest),red(lightest),shade);
        int gg=channel(green(darkest),green(lightest),shade);
        int bb=channel(blue(darkest),blue(lightest),shade);
        return (brass & 0xff000000) | (bb<<16) | (gg<<8) | rr;
    }
    private static int channel(int low,int high,double shade) { return (int)Math.round(low+(high-low)*shade); }
    // NativeImage stores ABGR, rather than Java's usual ARGB.
    private static int red(int color) { return color & 255; }
    private static int green(int color) { return (color>>>8) & 255; }
    private static int blue(int color) { return (color>>>16) & 255; }
    private static double brightness(int color) { return (red(color)*.2126+green(color)*.7152+blue(color)*.0722)/255; }
}
