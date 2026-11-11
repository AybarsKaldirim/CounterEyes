package com.countereyes.app;

final class MediaRange {
    static int[] parse(String range, int length) {
        if (length <= 0 || !range.matches("bytes=[0-9]*-[0-9]*")) return null;
        String[] values = range.substring(6).split("-", -1);
        try {
            if (values[0].isEmpty()) {
                long suffix = Long.parseLong(values[1]);
                return suffix > 0 ? new int[]{(int) Math.max(0, length - suffix), length - 1} : null;
            }
            long start = Long.parseLong(values[0]);
            long end = values[1].isEmpty() ? length - 1 : Long.parseLong(values[1]);
            return start < length && end >= start ? new int[]{(int) start, (int) Math.min(end, length - 1)} : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
