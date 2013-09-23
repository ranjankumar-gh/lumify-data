package com.altamiracorp.lumify.videoConversion;

import com.altamiracorp.lumify.ucd.artifact.VideoTranscript;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubRip {
    private static final Pattern TIME_PATTERN = Pattern.compile("([0-9]*):([0-9]*):([0-9]*),([0-9]*)");

    private enum ReadState {
        Frame,
        Time,
        Text
    }

    public static VideoTranscript read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return read(in);
        } finally {
            in.close();
        }
    }

    private static VideoTranscript read(InputStream in) throws IOException {
        VideoTranscript result = new VideoTranscript();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        ReadState readState = ReadState.Frame;
        VideoTranscript.Time time = null;
        String line;
        StringBuilder text = new StringBuilder();
        int frame;
        reader.read();
        while ((line = reader.readLine()) != null) {
            switch (readState) {
                case Frame:
                    frame = Integer.parseInt(line);
                    readState = ReadState.Time;
                    break;
                case Time:
                    time = parseTimeLine(line);
                    readState = ReadState.Text;
                    break;
                case Text:
                    if (line.trim().length() == 0) {
                        readState = ReadState.Frame;
                        result.add(time, text.toString().trim());
                        time = null;
                        text = new StringBuilder();
                        continue;
                    }
                    text.append(line.trim());
                    text.append(" ");
                    break;
            }
        }
        return result;
    }

    private static VideoTranscript.Time parseTimeLine(String line) {
        String start = line.substring(0, 12);
        String end = line.substring(line.length() - 12, line.length());
        return new VideoTranscript.Time(parseTime(start), parseTime(end));
    }

    private static long parseTime(String str) {
        Matcher m = TIME_PATTERN.matcher(str);
        if (!m.matches()) {
            throw new RuntimeException("Could not parse time: " + str);
        }
        return (Long.parseLong(m.group(1)) * 60 * 60 * 1000)
                + (Long.parseLong(m.group(2)) * 60 * 1000)
                + (Long.parseLong(m.group(3)) * 1000)
                + Long.parseLong(m.group(4));
    }
}
