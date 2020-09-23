package org.example;

import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Main {

    public static void main(String[] args) {

        URL catalogURL = Main.class.getResource("/catalog.xml");
        if (null == catalogURL)
            throw new IllegalArgumentException("Missing 'catalog.xml' in the classpath.");
        URI catalogURI = null;
        try {
            catalogURI = catalogURL.toURI();
        } catch (URISyntaxException e) {
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
        }

        System.out.println("Catalog: " + relativize(catalogURI.toString()));

        test1(catalogURI);
        test2(catalogURI);
        test3(catalogURI);
    }

    static void test1(URI catalogURI) {
        System.out.println("# Test1");
        Catalog c = makeStrictCatalog(catalogURI);
        resolveDerived(c);
        resolveSource(c);
    }

    static void test2(URI catalogURI) {
        System.out.println("# Test2");
        Catalog c = makeStrictCatalog(catalogURI);
        resolveSource(c);
        resolveDerived(c);
    }

    static void test3(URI catalogURI) {
        System.out.println("# Test3");
        Catalog c1 = makeStrictCatalog(catalogURI);
        resolveSource(c1);

        Catalog c2 = makeStrictCatalog(catalogURI);
        resolveDerived(c2);
    }

    static Catalog makeStrictCatalog(URI catalogURI) {
        return CatalogManager.catalog(CatalogFeatures.builder().with(CatalogFeatures.Feature.RESOLVE, "strict").build(), catalogURI);
    }

    static void showResult(String uri, String match, String expected) {
        boolean ok = match.endsWith(expected);
        System.out.println(uri + " -> " + relativize(match) + " As expected? " + ok);
    }

    static void resolveDerived(Catalog c) {
        String uri = "http://entailments/example.org/A/B/derived.ttl";
        String m = c.matchURI(uri);
        showResult(uri, m, "derived/A/B/derived.ttl");
    }

    static void resolveSource(Catalog c) {
        String uri = "http://example.org/A/B.owl";
        String m = c.matchURI(uri);
        showResult(uri, m, "sources/A/B.owl");
    }

    static URL here = Main.class.getResource("/");

    static String relativize(String path) {
        if (null == here) {
            // This case happens when the resources are compiled in the jar.
            int bang = path.indexOf("!");
            if (bang > 0)
                return path.substring(bang + 1);
            else
                return path;
        } else
            return path.replace(here.toString(), "/");
    }
}
