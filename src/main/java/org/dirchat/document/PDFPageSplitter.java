package org.dirchat.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFPageSplitter {
    public static class PageChunk {
        public final int pageNumber;
        public final String text;
        public PageChunk(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text;
        }
    }

    public static List<PageChunk> splitByPage(File pdfFile) throws IOException {
        List<PageChunk> chunks = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                chunks.add(new PageChunk(i, text));
            }
        }
        return chunks;
    }
}

