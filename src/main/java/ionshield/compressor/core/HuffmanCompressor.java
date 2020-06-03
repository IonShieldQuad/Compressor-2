package ionshield.compressor.core;

import ionshield.compressor.utils.BitInputStream;
import ionshield.compressor.utils.BitOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class HuffmanCompressor implements Compressor {
    
    List<String> extraData = new ArrayList<>();
    
    @Override
    public List<String> getExtraData() {
        return extraData;
    }
    
    @Override
    public List<String> compress(List<String> lines) {
        extraData = new ArrayList<>();
        List<String> out = new ArrayList<>();
        
        out.add("HUFFMAN");
        
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
        
        out.add(String.valueOf(string.length()));
    
        List<Node> nodes = new LinkedList<>();
        freq.forEach((key, value) -> nodes.add(new Node(key, null, null, value)));
        
        //Build the tree
        while (nodes.size() >= 2) {
            nodes.sort(Comparator.comparingInt(n -> n.weight));
            nodes.add(new Node(null, nodes.get(0), nodes.get(1), nodes.get(0).weight + nodes.get(1).weight));
            nodes.remove(0);
            nodes.remove(0);
        }
        
        String treeString = nodes.get(0).toString();
        out.add(treeString);
        Map<Character, String> codes = parseTreeToMap(treeString);
        
        //Output freq table
        for (Map.Entry<Character, String> entry : codes.entrySet()) {
            extraData.add((entry.getKey() == '\n' ? "\\n" : entry.getKey() == '\r' ? "\\r" : entry.getKey()) + ": " + freq.get(entry.getKey()) + " " + entry.getValue());
        }
        
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            String seq = codes.get(c);
            for (int j = 0; j < seq.length(); j++) {
                boolean bit = !(seq.charAt(j) == '0');
                try {
                    bs.writeBit(bit);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new IllegalArgumentException("IO Error");
                }
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
        
        out.add(Base64.getEncoder().encodeToString(bytes)/*new String(bytes)*/);
        
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
            if (!line.startsWith("HUFFMAN")) throw new IllegalArgumentException("File is invalid");
            l++;
    
            //Get message length
            line = lines.get(l);
            int msgLen = Integer.parseInt(line);
            l++;
    
            //Get tree
            line = lines.get(l);
            Node tree = parseTree(line);
            l++;
            
            //Concat strings
            StringBuilder sb = new StringBuilder();
            for (int i = l; i < lines.size(); i++) {
                sb.append(lines.get(i));
            }
            byte[] bytes = Base64.getDecoder().decode(sb.toString()) /*sb.toString().getBytes()*/;
    
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            BitInputStream bs = new BitInputStream(stream);
    
            sb = new StringBuilder();
            
            for (int i = 0; i < msgLen; i++) {
                Node curr = tree;
                
                //char c = 0;
                while (curr != null && curr.value == null) {
                    //char mask = (char) (0b1 << (15 - j));
                    try {
                        boolean bit = bs.readBit();
                        if (bit) {
                            curr = curr.right;
                        }
                        else {
                            curr = curr.left;
                        }
                        if (curr == null) {
                            throw new IOException("Sequence not in the tree");
                        }
                        if (curr.value != null) {
                            sb.append(curr.value);
                            break;
                        }
                        
                        //c = (char) ((c & ~mask) | (bit ? mask : 0b0));
                    } catch (EOFException e) {
                        i = msgLen;
                        //e.printStackTrace();
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new IllegalArgumentException("IO Error " + e.getMessage());
                    }
                }
            }
    
            out.add(sb.toString());
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("File is invalid");
        }
        return out;
    }
    
    private static Map<Character, String> parseTreeToMap(String string) {
        Map<Character, String> map = new HashMap<>();
        String path = "";
        int m = -1;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '(':
                {
                    if (m > 0) {
                        char val = (char)Integer.parseInt(string.substring(m, i));
                        map.put(val, path);
                        m = -1;
                    }
                    path = path + "0";
                }
                break;
                case '|':
                {
                    if (m > 0) {
                        char val = (char)Integer.parseInt(string.substring(m, i));
                        map.put(val, path);
                        m = -1;
                    }
                    path = path.substring(0, path.length() - 1) + "1";
                }
                break;
                case ')':
                {
                    if (m > 0) {
                        char val = (char)Integer.parseInt(string.substring(m, i));
                        map.put(val, path);
                        m = -1;
                    }
                    path = path.substring(0, path.length() - 1);
                }
                break;
                default:
                {
                    if (m < 0) {
                        m = i;
                    }
                }
            }
        }
        return map;
    }
    
    private static Node parseTree(String string) {
        Stack<Node> stack = new Stack<>();
        Stack<Boolean> path = new Stack<>();
        Node root = null;
        int m = -1;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '(':
                {
                    Character val = null;
                    if (m > 0) {
                        val = (char)Integer.parseInt(string.substring(m, i));
                        m = -1;
                    }
                    Node n = new Node(val, null, null);
                    if (root == null) {
                        root = n;
                    }
                    /*else {
                        if (path.peek()) {
                            stack.peek().right = n;
                        } else {
                            stack.peek().left = n;
                        }
                    }*/
                    
                    stack.push(n);
                    path.push(false);
                }
                break;
                case '|':
                {
                    Character val = null;
                    Node n;
                    if (m > 0) {
                        val = (char)Integer.parseInt(string.substring(m, i));
                        m = -1;
                        n = new Node(val, null, null);
                        if (path.peek()) {
                            stack.peek().right = n;
                        } else {
                            stack.peek().left = n;
                        }
                    }
                    
                    if (!path.empty()) {
                        path.pop();
                        path.push(true);
                    }
                }
                break;
                case ')':
                {
                    Character val = null;
                    Node n;
                    if (m > 0) {
                        val = (char)Integer.parseInt(string.substring(m, i));
                        m = -1;
                        n = new Node(val, null, null);
                        if (path.peek()) {
                            stack.peek().right = n;
                        } else {
                            stack.peek().left = n;
                        }
                    }
                    
                    if (!stack.empty() && !path.empty()) {
                        n = stack.pop();
                        path.pop();
    
                        if (!path.empty()) {
                            if (path.peek()) {
                                stack.peek().right = n;
                            } else {
                                stack.peek().left = n;
                            }
                        }
                    }
                }
                break;
                default:
                {
                    if (m < 0) {
                        m = i;
                    }
                }
            }
        }
        return root;
    }
    
    private static class Node {
        public Character value;
        public Node left;
        public Node right;
        public int weight;
        
        public Node(Character value, Node left, Node right) {
            this.value = value;
            this.left = left;
            this.right = right;
        }
    
        public Node(Character value, Node left, Node right, int weight) {
            this.value = value;
            this.left = left;
            this.right = right;
            this.weight = weight;
        }
        
        @Override
        public String toString() {
            if (value != null) {
                return Integer.toString(value);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            if (left != null) {
                sb.append(left.toString());
            }
            sb.append("|");
            if (right != null) {
                sb.append(right.toString());
            }
            sb.append(")");
            return sb.toString();
        }
    }
}
