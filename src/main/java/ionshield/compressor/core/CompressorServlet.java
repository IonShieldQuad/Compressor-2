package ionshield.compressor.core;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompressorServlet extends HttpServlet {
    private static final long serialVersionUID = 128L;
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        boolean useJSON = true;
        String folderName = "files";
        String path = getServletContext().getRealPath(folderName);
    
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
    
        File folder = new File(path);
        folder.mkdir();
    
        String text = "";
        if (req.getParameter("list") != null && req.getParameter("list").equals("all")) {
            resp.setContentType("application/json");
            try (Stream<Path> paths = Files.walk(Paths.get(path))) {
                sb.append("\"list\": [");
                List<Path> list = paths.filter(Files::isRegularFile).collect(Collectors.toList());
                for (int i = 0; i < list.size(); i++) {
                    File f = list.get(i).toFile();
                    sb.append("{\"name\": \"").append(f.getName()).append("\", \"size\": ").append(f.length()).append("}").append((i < list.size() - 1) ? ", ": "");
                }
                sb.append("]");
            }
        }
        else {
            if (req.getParameter("read") != null) {
                resp.setContentType("text/plain");
                useJSON = false;
                String name = req.getParameter("read");
                text = new String(Files.readAllBytes(Paths.get(path + "\\" + name)), StandardCharsets.UTF_8);
                sb.append("\"message\": \"").append(text).append("\"");
            }
        }
    
        sb.append("}");
        out.print(useJSON ? sb.toString() : text);
        out.close();
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String folderName = "files";
        String path = getServletContext().getRealPath(folderName);
        
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
    
        File folder = new File(path);
        folder.mkdir();
        
        if (req.getParameter("name") != null && req.getParameter("data") != null) {
            String name = req.getParameter("name");
            String data = req.getParameter("data");
            if (name.length() == 0) {
                sb.append("\"message\": \"Error: File name is empty\"");
            }
            else {
                File file = new File(path + "\\" + name);
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(data);
                writer.close();
                sb.append("\"message\": \"File \\\"").append(name).append("\\\": Submitted successfully\"");
            }
        }
        else {
            if (req.getParameter("delete") != null) {
                String name = req.getParameter("delete");
                if (name.length() == 0) {
                    sb.append("\"message\": \"Error: File name is empty\"");
                }
                else {
                    File file = new File(path + "\\" + name);
                    if (file.delete()) {
                        sb.append("\"message\": \"File \\\"").append(name).append("\\\": Deleted successfully\"");
                    }
                    else {
                        sb.append("\"message\": \"Failed to delete file\"");
                    }
                }
            }
            if (req.getParameter("encode") != null) {
                String name = req.getParameter("encode");
                String prefix = "e_";
                
                File file = new File(path + "\\" + name);
                File fileOut = new File(path + "\\" + prefix + name);
                if (file.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file)); BufferedWriter writer = new BufferedWriter(new FileWriter(fileOut))) {
                        List<String> l = new ArrayList<>();
                        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                            l.add(line);
                        }
                        
                        Compressor c = new HuffmanCompressor();
                        l = c.compress(l);
                        
                        for (int i = 0; i < l.size(); i++) {
                            writer.write(l.get(i));
                            if (i < l.size() - 1) {
                                writer.newLine();
                            }
                        }
                        sb.append("\"message\": \"Encoded\"");
                    }
                    catch (IllegalArgumentException e) {
                        sb.append("\"message\": \"Encoding error\"");
                    }
                }
                else {
                    sb.append("\"message\": \"File not found\"");
                }
            }
            else {
                if (req.getParameter("decode") != null) {
                    String name = req.getParameter("decode");
                    String prefix = "d_";
        
                    File file = new File(path + "\\" + name);
                    File fileOut = new File(path + "\\" + prefix + name);
                    if (file.exists()) {
                        try (BufferedReader reader = new BufferedReader(new FileReader(file)); BufferedWriter writer = new BufferedWriter(new FileWriter(fileOut))) {
                            List<String> l = new ArrayList<>();
                            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                                l.add(line);
                            }
                
                            Compressor c = new HuffmanCompressor();
                            l = c.decompress(l);
                
                            for (int i = 0; i < l.size(); i++) {
                                writer.write(l.get(i));
                                if (i < l.size() - 1) {
                                    writer.newLine();
                                }
                            }
                            sb.append("\"message\": \"Decoded\"");
                        }
                        catch (IllegalArgumentException e) {
                            sb.append("\"message\": \"Decoding error\"");
                        }
                    }
                    else {
                        sb.append("\"message\": \"File not found\"");
                    }
                }
            }
        }
        
        sb.append("}");
        out.print(sb.toString());
        out.close();
    }
}
