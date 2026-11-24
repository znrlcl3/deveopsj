package com.deveopsj.common.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

public final class HtmlExcelTableReader {
    private HtmlExcelTableReader() {}

    public static List<List<String>> read(byte[] bytes) throws IOException {
        String source;
        try { source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { source = Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        if (!source.stripLeading().toLowerCase(Locale.ROOT).startsWith("<!doctype html") && !source.stripLeading().toLowerCase(Locale.ROOT).startsWith("<html"))
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
        var rows = new ArrayList<List<String>>();
        new ParserDelegator().parse(new StringReader(source), new HTMLEditorKit.ParserCallback() {
            List<String> row; StringBuilder cell;
            final int[] pendingRows = new int[30];
            final BitSet occupied = new BitSet(30);
            int column, rowSpan, colSpan;
            private int span(MutableAttributeSet attributes, HTML.Attribute name) {
                Object raw=attributes.getAttribute(name);
                if(raw==null) return 1;
                try {
                    int value=Integer.parseInt(raw.toString());
                    if(value<1 || value>(name==HTML.Attribute.COLSPAN?30:4004)) throw new NumberFormatException();
                    return value;
                } catch(NumberFormatException e) { throw new IllegalArgumentException("병합 셀 범위를 확인하세요."); }
            }
            private void ensureColumn(int index) { while(row.size()<=index) row.add(""); }
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.TR) {
                    row=new ArrayList<>();occupied.clear();column=0;
                    for(int i=0;i<pendingRows.length;i++) if(pendingRows[i]>0) {
                        occupied.set(i);ensureColumn(i);pendingRows[i]--;
                    }
                }
                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && row!=null) {
                    column=occupied.nextClearBit(column);
                    rowSpan=span(attributes,HTML.Attribute.ROWSPAN);colSpan=span(attributes,HTML.Attribute.COLSPAN);
                    if(column+colSpan>30) throw new IllegalArgumentException("거래내역 열 수를 확인하세요.");
                    for(int i=column;i<column+colSpan;i++) if(occupied.get(i)) throw new IllegalArgumentException("병합 셀이 겹칩니다.");
                    cell=new StringBuilder();
                }
            }
            @Override public void handleText(char[] data, int pos) { if (cell != null) cell.append(data); }
            @Override public void handleEndTag(HTML.Tag tag, int pos) {
                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && row != null && cell != null) {
                    for(int i=column;i<column+colSpan;i++) {
                        ensureColumn(i);occupied.set(i);pendingRows[i]=rowSpan-1;
                    }
                    row.set(column,cell.toString().replace('\u00a0',' ').strip());
                    column+=colSpan;cell=null;
                }
                if (tag == HTML.Tag.TR && row != null) { rows.add(List.copyOf(row)); row = null; }
            }
        }, true);
        if (rows.size() > 4004) throw new IllegalArgumentException("한 번에 2,000행까지 가져올 수 있습니다.");
        return rows;
    }
}
