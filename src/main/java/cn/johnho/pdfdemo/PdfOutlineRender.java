package cn.johnho.pdfdemo;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.util.CollectionUtils;
import org.xmind.core.*;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class PdfOutlineRender {

    private String filePath;

    private String outPutXmindPath;

    private IWorkbook wb;

    private TreeMap<Integer, TopicWrapper> createdAndSortedTopicMap = new TreeMap<>();

    private PDPageTree pages;

    public PdfOutlineRender(String filePath, String outPutXmindPath) {
        this.filePath = filePath;
        this.outPutXmindPath = outPutXmindPath;
    }

    public static void main(String[] args) {
        new PdfOutlineRender("/Users/hezilong/Downloads/20190226quxin.pdf", "20190226quxin.xmind").render();
    }

    private void getOutlines(PDDocument document, PDOutlineNode node, TopicWrapper parentTopicWrapper) throws IOException {
        PDOutlineItem current = node.getFirstChild();
        while (current != null) {
            PDPage currentPage = current.findDestinationPage(document);
            final int pageNumber = pages.indexOf(currentPage) + 1;
            final ITopic currentTopic = wb.createTopic();
            currentTopic.setTitleText(current.getTitle());
            currentTopic.setFolded(true);

            parentTopicWrapper.getTopic().add(currentTopic, ITopic.ATTACHED);
            final TopicWrapper currentTopicWrapper = new TopicWrapper(currentTopic, parentTopicWrapper, pageNumber);
            this.createdAndSortedTopicMap.put(pageNumber, currentTopicWrapper);
            getOutlines(document, current, currentTopicWrapper);
            current = current.getNextSibling();
        }
    }

    public void render() {
        try (PDDocument file = PDDocument.load(new File(this.filePath))) {
            // create root topic
            final IWorkbookBuilder workbookBuilder = Core.getWorkbookBuilder();
            wb = workbookBuilder.createWorkbook(this.outPutXmindPath);
            final ISheet primarySheet = wb.getPrimarySheet();
            final ITopic rootTopic = primarySheet.getRootTopic();
            rootTopic.setStructureClass("org.xmind.ui.map.clockwise");

            // iterate pdf outline
            PDDocumentOutline outline = file.getDocumentCatalog().getDocumentOutline();
            final PDOutlineItem firstChild = outline.getFirstChild();
            if (firstChild == null) {
                System.out.println("no outline found");
                return;
            }

            rootTopic.setTitleText(firstChild.getTitle());
            rootTopic.setFolded(true);
            final TopicWrapper rootTopicWrapper = new TopicWrapper(rootTopic, null, 1);
            createdAndSortedTopicMap.put(1, rootTopicWrapper);
            pages = file.getDocumentCatalog().getPages();
            getOutlines(file, outline, rootTopicWrapper);

            //iterate page and get highlighted annotation
            final int numberOfPages = file.getNumberOfPages();
            for (int i = 0; i < numberOfPages; i++) {
                final PDPage page = file.getPage(i);
                final ArrayList<String> highlightedText = getHighlightedTextByPage(page);
                if (!CollectionUtils.isEmpty(highlightedText)) {
                    final Map.Entry<Integer, TopicWrapper> floorEntry = createdAndSortedTopicMap.floorEntry(i+1);
                    if (floorEntry != null) {
                        final TopicWrapper targetTopic = floorEntry.getValue();
                        highlightedText.forEach(item -> {
                            final ITopic t = wb.createTopic();
                            TopicWrapper parentTopicWrapper = targetTopic.getParentTopicWrapper();
                            t.setTitleText(item.replace("\n", ""));
                            targetTopic.getTopic().add(t);
                            targetTopic.getTopic().setFolded(false);
                            while (parentTopicWrapper != null && parentTopicWrapper.getTopic().isFolded()) {
                                parentTopicWrapper.getTopic().setFolded(false);
                                parentTopicWrapper = parentTopicWrapper.getParentTopicWrapper();
                            }
                        });
                    }
                }
            }

            // save file
            wb.save(this.outPutXmindPath);
        } catch (IOException | CoreException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> getHighlightedTextByPage(PDPage page) throws IOException {
        ArrayList<String> highlightedTexts = new ArrayList<>();
        // get  annotation dictionaries
        List<PDAnnotation> annotations = page.getAnnotations();

        for(int i=0; i<annotations.size(); i++) {
            // check subType
            if("Highlight".equals(annotations.get(i).getSubtype())) {
                // extract highlighted text
                PDFTextStripperByArea stripperByArea = new PDFTextStripperByArea();

                COSArray quadsArray = (COSArray) annotations.get(i).getCOSObject().getDictionaryObject(COSName.getPDFName("QuadPoints"));
                String str = null;

                for(int j=1, k=0; j<=(quadsArray.size()/8); j++) {

                    COSFloat ULX = (COSFloat) quadsArray.get(0+k);
                    COSFloat ULY = (COSFloat) quadsArray.get(1+k);
                    COSFloat URX = (COSFloat) quadsArray.get(2+k);
                    COSFloat URY = (COSFloat) quadsArray.get(3+k);
                    COSFloat LLX = (COSFloat) quadsArray.get(4+k);
                    COSFloat LLY = (COSFloat) quadsArray.get(5+k);
                    COSFloat LRX = (COSFloat) quadsArray.get(6+k);
                    COSFloat LRY = (COSFloat) quadsArray.get(7+k);

                    k+=8;

                    float ulx = ULX.floatValue() - 1;                           // upper left x.
                    float uly = ULY.floatValue();                               // upper left y.
                    float width = URX.floatValue() - LLX.floatValue();          // calculated by upperRightX - lowerLeftX.
                    float height = URY.floatValue() - LLY.floatValue();         // calculated by upperRightY - lowerLeftY.

                    PDRectangle pageSize = page.getMediaBox();
                    uly = pageSize.getHeight() - uly;

                    Rectangle2D.Float rectangle_2 = new Rectangle2D.Float(ulx, uly, width, height);
                    stripperByArea.addRegion("highlightedRegion", rectangle_2);
                    stripperByArea.extractRegions(page);
                    String highlightedText = stripperByArea.getTextForRegion("highlightedRegion");

                    if(j > 1) {
                        str = str.concat(highlightedText);
                    } else {
                        str = highlightedText;
                    }
                }
                highlightedTexts.add(str);
            }
        }
        return highlightedTexts;
    }
}
