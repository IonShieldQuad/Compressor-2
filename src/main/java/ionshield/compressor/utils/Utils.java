package ionshield.compressor.utils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Utils {
    
    
    public static BufferedImage readImage(List<String> lines) {
        BufferedImage image;
        try {
            int l = 0;
            String line;
            
            line = lines.get(l);
            if (!line.equals("RLE 1.0")) throw new IllegalArgumentException("File is invalid");
            l++;
    
            line = lines.get(l);
            if (!line.equals("head")) throw new IllegalArgumentException("File is invalid");
            l++;
    
            line = lines.get(l);
            if (!line.startsWith("t")) throw new IllegalArgumentException("File is invalid");
            boolean compressed = !line.split(" ")[1].equals("0");
            l++;
    
            line = lines.get(l);
            if (!line.startsWith("s")) throw new IllegalArgumentException("File is invalid");
            int width = Integer.parseInt(line.split(" ")[1]);
            int height = Integer.parseInt(line.split(" ")[2]);
            l++;
    
            int bg = 0x000000;
            int fg = 0xffffff;
            
            line = lines.get(l);
            if (line.startsWith("c")) {
                bg = Integer.parseInt(line.split(" ")[1], 16);
                fg = Integer.parseInt(line.split(" ")[2], 16);
                l++;
            }
    
            line = lines.get(l);
            if (!line.equals("data")) throw new IllegalArgumentException("File is invalid");
            l++;
    
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int index = 0;
            
            for (int i = l; i < lines.size(); i++) {
                line = lines.get(i);
                if (compressed) {
                    String[] values = line.split(" ");
                    for (int j = 0; j < (values.length & ~1); j += 2) {
                        boolean value = !values[j].equals("0");
                        int count = Integer.parseInt(values[j + 1]);
                        for (int k = 0; k < count; k++) {
                            if (index >= width * height) return image;
                            if (value) {
                                image.setRGB(index % width, index / width, fg);
                            }
                            else {
                                image.setRGB(index % width, index / width, bg);
                            }
                            index++;
                        }
                    }
                }
                else {
                    for (int j = 0; j < line.length(); j++) {
                        char c = line.charAt(j);
                        if (index >= width * height) return image;
                        if (c == '0') {
                            image.setRGB(index % width, index / width, bg);
                            index++;
                        }
                        if (c == '1') {
                            image.setRGB(index % width, index / width, fg);
                            index++;
                        }
                    }
                }
            }
            
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("File is invalid");
        }
        return image;
    }
    
    public static List<String> compress(List<String> lines) {
        List<String> out = new ArrayList<>();
        try {
            int l = 0;
            String line;
            
            line = lines.get(l);
            if (!line.equals("RLE 1.0")) throw new IllegalArgumentException("File is invalid");
            out.add(line);
            l++;
    
            line = lines.get(l);
            if (!line.equals("head")) throw new IllegalArgumentException("File is invalid");
            out.add(line);
            l++;
    
            line = lines.get(l);
            if (!line.startsWith("t")) throw new IllegalArgumentException("File is invalid");
            boolean compressed = !line.split(" ")[1].equals("0");
            if (compressed) {
                throw new IllegalArgumentException("File is already compressed");
            }
            out.add("t 1");
            l++;
    
            line = lines.get(l);
            if (!line.startsWith("s")) throw new IllegalArgumentException("File is invalid");
            int width = Integer.parseInt(line.split(" ")[1]);
            int height = Integer.parseInt(line.split(" ")[2]);
            out.add(line);
            l++;
    
            int bg = 0x000000;
            int fg = 0xffffff;
    
            line = lines.get(l);
            if (line.startsWith("c")) {
                bg = Integer.parseInt(line.split(" ")[1], 16);
                fg = Integer.parseInt(line.split(" ")[2], 16);
                out.add(line);
                l++;
            }
    
            line = lines.get(l);
            if (!line.equals("data")) throw new IllegalArgumentException("File is invalid");
            out.add(line);
            l++;
            
            StringBuilder lineOut = new StringBuilder();
            boolean isOne = false;
            int count = 0;
            
            for (int i = l; i < lines.size(); i++) {
                line = lines.get(i);
    
                for (int j = 0; j < line.length(); j++) {
                    char c = line.charAt(j);
                    
                    if (c == '0') {
                        if (isOne) {
                           if (count > 0) {
                               lineOut.append('1').append(" ").append(count).append(" ");
                           }
                            count = 1;
                            isOne = false;
                        }
                        else {
                            count++;
                        }
                    }
                    if (c == '1') {
                        if (!isOne) {
                            if (count > 0) {
                                lineOut.append('0').append(" ").append(count).append(" ");
                            }
                            count = 1;
                            isOne = true;
                        }
                        else {
                            count++;
                        }
                    }
                    
                }
                
            }
    
            if (count > 0) {
                lineOut.append(isOne ? '1' : '0').append(" ").append(count);
            }
            
            out.add(lineOut.toString());
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                throw new IllegalArgumentException("File is invalid");
        }
        return out;
    }
    
    public static List<String> decompress(List<String> lines) {
        List<String> out = new ArrayList<>();
        try {
            int l = 0;
            String line;
            
            line = lines.get(l);
            if (!line.equals("RLE 1.0")) throw new IllegalArgumentException("File is invalid");
            out.add(line);
            l++;
            
            line = lines.get(l);
            if (!line.equals("head")) throw new IllegalArgumentException("File is invalid");
            out.add(line);
            l++;
            
            line = lines.get(l);
            if (!line.startsWith("t")) throw new IllegalArgumentException("File is invalid");
            boolean compressed = !line.split(" ")[1].equals("0");
            if (!compressed) {
                throw new IllegalArgumentException("File is already uncompressed");
            }
            out.add("t 0");
            l++;
            
            line = lines.get(l);
            if (!line.startsWith("s")) throw new IllegalArgumentException("File is invalid");
            int width = Integer.parseInt(line.split(" ")[1]);
            int height = Integer.parseInt(line.split(" ")[2]);
            out.add(line);
            l++;
            
            int bg = 0x000000;
            int fg = 0xffffff;
            
            line = lines.get(l);
            if (line.startsWith("c")) {
                bg = Integer.parseInt(line.split(" ")[1], 16);
                fg = Integer.parseInt(line.split(" ")[2], 16);
                out.add(line);
                l++;
            }
            
            line = lines.get(l);
            if (!line.equals("data")) throw new IllegalArgumentException("File is invalid");
            out.add(line);
            l++;
            
            StringBuilder lineOut = new StringBuilder();
            
            for (int i = l; i < lines.size(); i++) {
                line = lines.get(i);
    
                String[] values = line.split(" ");
                for (int j = 0; j < (values.length & ~1); j += 2) {
                    boolean value = !values[j].equals("0");
                    int count = Integer.parseInt(values[j + 1]);
                    for (int k = 0; k < count; k++) {
                        if (value) {
                            lineOut.append('1');
                        }
                        else {
                            lineOut.append('0');
                        }
                        if (lineOut.length() >= width) {
                            out.add(lineOut.toString());
                            lineOut = new StringBuilder();
                        }
                    }
                }
                
            }
            
            if (lineOut.length() > 0) {
                out.add(lineOut.toString());
            }
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("File is invalid");
        }
        return out;
    }
    
    public static int getSize(List<String> lines) {
        int size = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            size += line.length();
            if (i < lines.size() - 1) {
                size += System.lineSeparator().length();
            }
        }
        /*for (String line : lines) {
            size += line.length() + System.lineSeparator().length();
        }
        size -= System.lineSeparator().length();*/
        return size;
    }
}
