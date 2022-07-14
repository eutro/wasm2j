package io.github.eutro.wasm2j.ir;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

public class SSADisplay {
    private static final int BLOCK_PADDING = 5;
    private static final int INTERBLOCK_PADDING = 25;
    private static final int FONT_SIZE = 25;
    public static final DOMImplementation DOM_IMPL;

    static {
        try {
            DOM_IMPL = DOMImplementationRegistry.newInstance().getDOMImplementation("XML 1.0");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String SVG_NS = "http://www.w3.org/2000/svg";
    public static final int LINE_LIMIT = 300;

    public static void debugDisplayToFile(Document img, String file) {
        File jFile = new File(file);
        jFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(jFile)) {
            TransformerFactory.newInstance()
                    .newTransformer()
                    .transform(
                            new DOMSource(img),
                            new StreamResult(writer)
                    );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    public static Document displaySSA(SSA.Function func) {
        Document document = createSvg();
        Element rootGroup = document.createElement("g");
        rootGroup.setAttribute("font-family", "monospace");
        rootGroup.setAttribute("font-size", "25px");
        Element rootElement = (Element) document.getFirstChild();

        Element style = document.createElement("style");
        style.setTextContent(
                "line.out{stroke:green;stroke-width:5;}" +
                        "line.target{stroke:orange;stroke-width:5;}" +
                        ".block{cursor:pointer;}"
        );
        rootElement.appendChild(style);

        Element script = document.createElement("script");
        script.setTextContent(
                "function setClassesTo(targetClass,toSet){" +
                        "[...document.getElementsByClassName(toSet)].forEach(it=>it.classList.remove(toSet));" +
                        "[...document.getElementsByClassName(targetClass)].forEach(it=>it.classList.add(toSet));" +
                        "}"
        );
        rootElement.appendChild(script);

        rootElement.appendChild(rootGroup);

        List<List<SSA.BasicBlock>> layers = new ArrayList<>();

        class QueueEntry {
            final SSA.BasicBlock block;
            final int depth;

            QueueEntry(SSA.BasicBlock block, int depth) {
                this.block = block;
                this.depth = depth;
            }
        }
        Deque<QueueEntry> queue = new ArrayDeque<>();
        Set<SSA.BasicBlock> seen = new HashSet<>();
        SSA.BasicBlock root = func.blocks.get(0);
        queue.add(new QueueEntry(root, 0));
        seen.add(root);
        while (!queue.isEmpty()) {
            QueueEntry entry = queue.removeFirst();
            if (layers.size() <= entry.depth) {
                layers.add(new ArrayList<>());
            }
            layers.get(entry.depth).add(entry.block);
            for (SSA.BasicBlock target : entry.block.control.targets()) {
                if (seen.add(target)) {
                    queue.addLast(new QueueEntry(target, entry.depth + 1));
                }
            }
        }

        class ImgCell {
            final Image img;
            final SSA.BasicBlock bb;
            final int dx, y;
            int cx, cy;

            ImgCell(Image img, SSA.BasicBlock bb, int x, int y) {
                this.img = img;
                this.bb = bb;
                this.dx = x;
                this.y = y;
            }
        }
        Map<SSA.BasicBlock, ImgCell> blockCells = new HashMap<>();

        class ImgLayer {
            final List<ImgCell> cells;
            final int layerWidth;
            int layerX;

            ImgLayer(List<ImgCell> cells, int layerWidth, int layerX) {
                this.cells = cells;
                this.layerWidth = layerWidth;
                this.layerX = layerX;
            }
        }

        int imgHeight = INTERBLOCK_PADDING;
        int imgWidth = 1;

        List<ImgLayer> imgLayers = new ArrayList<>();
        for (List<SSA.BasicBlock> layer : layers) {
            int layerHeight = 0;
            int layerWidth = INTERBLOCK_PADDING;
            ArrayList<ImgCell> cells = new ArrayList<>(layer.size());
            for (SSA.BasicBlock block : layer) {
                Image blockImg = displayBlock(block);

                ImgCell cell = new ImgCell(blockImg, block, layerWidth, imgHeight);
                blockCells.put(block, cell);
                cells.add(cell);

                layerWidth += blockImg.width + INTERBLOCK_PADDING;
                layerHeight = Math.max(layerHeight, blockImg.height);
            }

            imgHeight += layerHeight + INTERBLOCK_PADDING;
            imgWidth = Math.max(imgWidth, layerWidth);

            imgLayers.add(new ImgLayer(cells, layerWidth, -1));
        }

        rootElement.setAttribute("width", "" + (int) Math.ceil(imgWidth));
        rootElement.setAttribute("height", "" + (int) Math.ceil(imgHeight));
        rootElement.setAttribute("stroke-width", "1");
        rootElement.setAttribute("stroke", "black");
        for (ImgLayer layer : imgLayers) {
            layer.layerX = (imgWidth - layer.layerWidth) / 2;
            for (ImgCell cell : layer.cells) {
                cell.cx = layer.layerX + cell.dx + cell.img.width / 2;
                cell.cy = cell.y + cell.img.height / 2;
            }
        }
        for (Map.Entry<SSA.BasicBlock, ImgCell> entry : blockCells.entrySet()) {
            ImgCell cell = entry.getValue();
            SSA.BasicBlock block = entry.getKey();
            for (SSA.BasicBlock target : block.control.targets()) {
                ImgCell targetCell = blockCells.get(target);
                Element line = document.createElement("line");
                rootGroup.appendChild(line);
                line.setAttribute("x1", Float.toString(cell.cx));
                line.setAttribute("y1", Float.toString(cell.cy + ((cell.img.height - BLOCK_PADDING) / 2.0f)));
                line.setAttribute("x2", Float.toString(targetCell.cx));
                line.setAttribute("y2", Float.toString(targetCell.cy - ((targetCell.img.height - BLOCK_PADDING) / 2.0f)));
                line.setAttribute("class", String.format("%s-out %s-target", block.toTargetString(), target.toTargetString()));
            }
        }
        for (ImgLayer layer : imgLayers) {
            for (ImgCell cell : layer.cells) {
                int x = layer.layerX + cell.dx;
                int y = cell.y;
                Element cellGroup = document.createElement("g");
                cellGroup.setAttribute("class", "block");
                cellGroup.setAttribute("onmouseover", String.format(
                        "setClassesTo(\"%1$s-out\",\"out\");"
                                +"setClassesTo(\"%1$s-target\",\"target\");",
                        cell.bb.toTargetString()
                ));
                Element rect = document.createElement("rect");
                rect.setAttribute("fill", "white");
                rect.setAttribute("width", "" + cell.img.width);
                rect.setAttribute("height", "" + cell.img.height);
                rect.setAttribute("x", "" + x);
                rect.setAttribute("y", "" + y);
                cellGroup.appendChild(rect);
                cellGroup.appendChild(cell.img.drawer.draw(document, x, y));
                rootGroup.appendChild(cellGroup);
            }
        }

        return document;
    }

    private static Document createSvg() {
        return DOM_IMPL.createDocument(SVG_NS, "svg", null);
    }

    public interface Drawer {
        Element draw(Document document, float dx, float dy);
    }

    public static class Image {
        public final int width, height;
        public final Drawer drawer;

        public Image(int width, int height, Drawer drawer) {
            this.width = width;
            this.height = height;
            this.drawer = drawer;
        }
    }

    public static Image displayBlock(SSA.BasicBlock block) {
        String str = block.toString();
        Font mono = getMonoFont();
        FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, false);

        String[] lines = Arrays.stream(str.split("\n")).flatMap(s -> {
            AttributedString abs = new AttributedString(s);
            AttributedCharacterIterator absIter = abs.getIterator();
            LineBreakMeasurer lbr = new LineBreakMeasurer(absIter, frc);

            Stream.Builder<String> sb = Stream.builder();
            int lastPos = 0;
            while (lbr.getPosition() < absIter.getEndIndex()) {
                lbr.nextLayout(LINE_LIMIT);
                int pos = lbr.getPosition();
                String rawLine = s.substring(lastPos, pos);
                sb.add(lastPos == 0 ? rawLine : "   " + rawLine);
                lastPos = pos;
            }
            return sb.build();
        }).toArray(String[]::new);
        TextLayout[] layouts = new TextLayout[lines.length];
        for (int i = 0; i < lines.length; i++) {
            layouts[i] = new TextLayout(lines[i], mono, frc);
        }

        float maxWidth = 0;
        float height = 0;
        for (TextLayout layout : layouts) {
            height += layout.getAscent()
                    + layout.getDescent()
                    + layout.getLeading();
            maxWidth = Math.max(maxWidth, layout.getAdvance());
        }

        return new Image(
                (int) Math.ceil(maxWidth + 2 * BLOCK_PADDING),
                (int) Math.ceil(height + 2 * BLOCK_PADDING),
                (document, dx, dy) -> {
                    Element group = document.createElement("g");

                    float y = BLOCK_PADDING;
                    for (int i = 0; i < layouts.length; i++) {
                        TextLayout layout = layouts[i];
                        y += layout.getAscent();
                        Element text = document.createElement("text");
                        text.setTextContent(lines[i]);
                        text.setAttribute("xml:space", "preserve");
                        text.setAttribute("stroke", "none");
                        text.setAttribute("x", Float.toString(BLOCK_PADDING + dx));
                        text.setAttribute("y", Float.toString(y + dy));
                        group.appendChild(text);

                        y += layout.getDescent()
                                + layout.getLeading();
                    }
                    return group;
                }
        );
    }

    private static Font monoFont = null;

    private static Font getMonoFont() {
        if (monoFont != null) return monoFont;
        return monoFont = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);
    }
}
