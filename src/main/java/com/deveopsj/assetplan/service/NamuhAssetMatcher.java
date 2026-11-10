package com.deveopsj.assetplan.service;
import com.deveopsj.assetplan.entity.InvestmentAsset;
import java.text.Normalizer;
import java.util.*;
public final class NamuhAssetMatcher {
    private NamuhAssetMatcher() {}
    public record Option(Long id,String symbol,String label) {}
    public record Match(InvestmentAsset automatic,List<Option> options) {}
    public static boolean supported(InvestmentAsset a) { return a.isActive() && "USD".equals(a.getCurrency()) && Set.of("NASDAQ","NYSE","NYSE_ARCA","NYSE_AMERICAN","CBOE_BZX","IEX").contains(a.getMarket()); }
    public static String normalize(String value) {
        String text=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
        text=text.replaceAll("\\bAND\\b","&").replaceAll("(?i)\\s*[-,]?\\s+COMMON STOCK$","");
        return text.replaceAll("[^\\p{L}\\p{N}]","");
    }
    public static Match match(String name,List<InvestmentAsset> assets) {
        String key=normalize(name);
        var exact=assets.stream().filter(NamuhAssetMatcher::supported).filter(a->!key.isEmpty() && normalize(a.getAssetName()).equals(key)).toList();
        if(exact.size()==1) return new Match(exact.get(0),List.of(option(exact.get(0))));
        var tokens=new HashSet<>(Arrays.asList(name.toUpperCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")));
        tokens.removeAll(Set.of("","ETF","INC","THE","AND","COMMON","STOCK"));
        var suggestions=assets.stream().filter(NamuhAssetMatcher::supported)
            .filter(a->exact.contains(a) || score(a,tokens)>0)
            .sorted(Comparator.<InvestmentAsset>comparingInt(a->exact.contains(a)?1000:score(a,tokens)).reversed().thenComparing(InvestmentAsset::getSymbol).thenComparing(InvestmentAsset::getId))
            .limit(10).map(NamuhAssetMatcher::option).toList();
        return new Match(null,suggestions);
    }
    private static int score(InvestmentAsset asset,Set<String> tokens) {
        var words=new HashSet<>(Arrays.asList(asset.getAssetName().toUpperCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")));
        return (int)tokens.stream().filter(words::contains).count();
    }
    public static Option option(InvestmentAsset a) { return new Option(a.getId(),a.getSymbol(),"["+a.getSymbol()+"] "+a.getAssetName()+" · "+a.getMarket()); }
}
