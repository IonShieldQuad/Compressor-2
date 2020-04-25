package ionshield.compressor.core;

import ionshield.compressor.utils.BitInputStream;
import ionshield.compressor.utils.BitOutputStream;
import ionshield.compressor.utils.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class ArithmeticCompressor implements Compressor {
    
    List<String> extraData = new ArrayList<>();
    
    @Override
    public List<String> getExtraData() {
        return extraData;
    }
    
    @Override
    public List<String> compress(List<String> lines) {
        extraData = new ArrayList<>();
        List<String> out = new ArrayList<>();
        
        out.add("ARITHMETIC");
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) {
                sb.append(System.lineSeparator());
            }
        }
        String string = sb.toString();
        
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        BitOutputStream bs = new BitOutputStream(stream);
        Map<Character, Integer> freq = new HashMap<>();
        
        //Frequency scan
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            freq.put(c, (freq.get(c) == null ? 1 : (freq.get(c) + 1)));
        }
        
        //Output message length
        int msgLen = string.length();
        out.add(String.valueOf(msgLen));
        
        //Output size of freq table
        out.add(String.valueOf(freq.size()));
        
        //Output freq table
        int w = 0;
        for (Map.Entry<Character, Integer> entry : freq.entrySet()) {
            out.add((int)entry.getKey() + " " + entry.getValue());
            extraData.add((entry.getKey() == '\n' ? "\\n" : entry.getKey() == '\r' ? "\\r" : entry.getKey()) + ": " + entry.getValue() + " (" + new BigDecimal((double)w / msgLen).setScale(getPrecision(msgLen), BigDecimal.ROUND_HALF_EVEN) + "; " + new BigDecimal(((double)w + entry.getValue()) / msgLen).setScale(getPrecision(msgLen), BigDecimal.ROUND_HALF_EVEN) + ")");
            w += entry.getValue();
        }
        
        Map<Character, Pair<BigDecimal, BigDecimal>> intervals = calculateIntervals(freq, msgLen);
        
        BigDecimal lower = new BigDecimal(0);
        BigDecimal upper = new BigDecimal(1);
        BigDecimal range;
        //Arithmetic encoding
        for (int i = 0; i < msgLen; i++) {
            char c = string.charAt(i);
            range = upper.subtract(lower);
            upper = lower.add(range.multiply(intervals.get(c).b));
            lower = lower.add(range.multiply(intervals.get(c).a));
        }
        BigDecimal number = lower.add(upper).divide(new BigDecimal(2), BigDecimal.ROUND_HALF_EVEN).setScale(msgLen + getPrecision(msgLen), BigDecimal.ROUND_HALF_EVEN);
        //Output number scale
        out.add(String.valueOf(number.scale()));
    
        extraData.add("");
        extraData.add(number.toString());
        byte[] inBytes = number.unscaledValue().toByteArray();
        
        //Output number of bytes
        out.add(String.valueOf(inBytes.length));
        
        for (int i = 0; i < inBytes.length; i++) {
            try {
                bs.writeByte(inBytes[i]);
            }
            catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        
        try {
            bs.flush();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("IO Error");
        }
        byte[] bytes = stream.toByteArray();
        //extraData.add("");
        StringBuilder extraBuilder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i % 20 == 0) {
                extraData.add(extraBuilder.toString());
                extraBuilder = new StringBuilder();
            }
            /*if (i > 10000) {
                break;
            }*/
            extraBuilder.append(Integer.toString((bytes[i] & 0xf0) >> 4, 16));
            extraBuilder.append(Integer.toString(bytes[i] & 0x0f, 16));
            extraBuilder.append(" ");
        }
        if (extraBuilder.length() > 0) {
            extraData.add(extraBuilder.toString());
        }
        
        out.add(new String(bytes));
        
        return out;
    }
    
    @Override
    public List<String> decompress(List<String> lines) {
        extraData = new ArrayList<>();
        List<String> out = new ArrayList<>();
        try {
            int l = 0;
            String line;
            
            //Check header
            line = lines.get(l);
            if (!line.startsWith("ARITHMETIC")) throw new IllegalArgumentException("File is invalid");
            l++;
            
            //Get message length
            line = lines.get(l);
            int msgLen = Integer.parseInt(line);
            l++;
    
            //Get table length
            line = lines.get(l);
            int tableLen = Integer.parseInt(line);
            l++;
            
            //Get table
            Map<Character, Integer> freq = new HashMap<>();
            for (int i = 0; i < tableLen; i++, l++) {
                line = lines.get(l);
                String[] res = line.split("\\s+");
                freq.put((char)Integer.parseInt(res[0]), Integer.parseInt(res[1]));
            }
            
            Map<Character, Pair<BigDecimal, BigDecimal>> intervals = calculateIntervals(freq, msgLen);
    
            //Get scale
            line = lines.get(l);
            int scale = Integer.parseInt(line);
            l++;
    
            //Get number of bytes
            line = lines.get(l);
            int bytesNum = Integer.parseInt(line);
            l++;
            
            //Concat strings
            StringBuilder sb = new StringBuilder();
            for (int i = l; i < lines.size(); i++) {
                sb.append(lines.get(i));
            }
            byte[] bytes = sb.toString().getBytes();
            
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            BitInputStream bs = new BitInputStream(stream);
            
            byte[] outBytes = new byte[bytesNum];
            
            for (int i = 0; i < bytesNum; i++) {
                try {
                    outBytes[i] = bs.readByte();
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            
            Map<BigDecimal, Character> intervalsInv = new HashMap<>();
            List<BigDecimal> intervalsList = new ArrayList<>();
            
            for (Map.Entry<Character, Pair<BigDecimal, BigDecimal>> entry : intervals.entrySet()) {
                intervalsInv.put(entry.getValue().a, entry.getKey());
                intervalsList.add(entry.getValue().a);
            }
            intervalsList.add(new BigDecimal(1));
            
            sb = new StringBuilder();
            //Arithmetic decoding
            BigDecimal number = new BigDecimal(new BigInteger(outBytes), scale);
            
            for (int i = 0; i < msgLen; i++) {
                char c = 0;
                int low = 0;
                int high = intervalsList.size() - 1;
                int mid = (high + low) / 2;
                while (high > low) {
                    mid = (high + low) / 2;
                    BigDecimal val0 = intervalsList.get(mid);
                    BigDecimal val1 = intervalsList.get(mid + 1);
                    if (number.compareTo(val0) < 0) {
                        high = mid;
                        continue;
                    }
                    if (number.compareTo(val1) >= 0) {
                        low = mid + 1;
                        continue;
                    }
                    break;
                }
                c = intervalsInv.get(intervalsList.get(mid));
                System.out.println(i + ": " + c);
                /*for (Map.Entry<Character, Pair<BigDecimal, BigDecimal>> entry : intervals.entrySet()) {
                    if (number.compareTo(entry.getValue().a) >= 0 && number.compareTo(entry.getValue().b) <= 0) {
                        c = entry.getKey();
                        break;
                    }
                }*/
                sb.append(c);
                BigDecimal interval = intervals.get(c).b.subtract(intervals.get(c).a);
                number = number.subtract(intervals.get(c).a);
                number = number.divide(interval, BigDecimal.ROUND_HALF_EVEN);
                //number = number.setScale(number.scale() - 1, BigDecimal.ROUND_HALF_EVEN);
            }
            
            out.add(sb.toString());
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("File is invalid");
        }
        return out;
    }
    
    private Map<Character, Pair<BigDecimal, BigDecimal>> calculateIntervals(Map<Character, Integer> freq, int total) {
        BigDecimal t = new BigDecimal(total);
        int w = 0;
        int scale = getPrecision(total);
        Map<Character, Pair<BigDecimal, BigDecimal>> m = new HashMap<>();
        
        for (Map.Entry<Character, Integer> entry : freq.entrySet()) {
            BigDecimal a = new BigDecimal(w);
            a = a.setScale(scale, BigDecimal.ROUND_HALF_EVEN);
            a = a.divide(t, BigDecimal.ROUND_HALF_EVEN);
    
            BigDecimal b = new BigDecimal(w + entry.getValue());
            b = b.setScale(scale, BigDecimal.ROUND_HALF_EVEN);
            b = b.divide(t, BigDecimal.ROUND_HALF_EVEN);
            
            m.put(entry.getKey(), new Pair<>(a, b));
            w += entry.getValue();
        }
        
        return m;
    }
    
    private int getPrecision(int length) {
        return 1 + (int)Math.ceil(Math.log10(length + 1));
    }
}
