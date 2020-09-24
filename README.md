# This project demonstrates a bug in the Open JDK 11 through 15 XML Catalog API implementation.

See: [JDK-8253569](https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8253569)

## Bug.

The implementation of `String javax.xml.catalog.GroupEntry.matchURI(String)` modifies two state variables `longestRewriteMatch` and `longestSuffixMatch`.

There is clearly an intent to support resetting these variables via the `javax.xml.catalog.GroupEntry.reset()` method;
however, this method is not part of the public API not is it invoked internally.

Consequently, the calls to `String javax.xml.catalog.RewriteUri.match(String, int)` and `String javax.xml.catalog.UriSuffix.match(String, int)`
will be effectively returning `null` if the current `longestRewriteMatch` or `longestSuffixMatch` respectively exceed the length of the catalog entry.

## Reproducible behavior

To run:

- Install Java (I use [sdkman](https://sdkman.io/)

- Make sure `JAVA_HOME` is set (tested with `15.0.0.hs-adpt`, `14.0.2.j9-adpt` and `11.0.7.hs-adpt`)

- Run via the terminal: `sbt run`

  Alternatively, import in IntelliJ as `SBT` project and run it from there.
  
- The output is as follows:

```
Catalog: /catalog.xml
# Test1
http://entailments/example.org/A/B/derived.ttl -> /derived/A/B/derived.ttl As expected? true
http://example.org/A/B.owl -> /derived/A/B/derived.ttl As expected? false
# Test2
http://example.org/A/B.owl -> /sources/A/B.owl As expected? true
http://entailments/example.org/A/B/derived.ttl -> /derived/A/B/derived.ttl As expected? true
# Test3
http://example.org/A/B.owl -> /sources/A/B.owl As expected? true
http://entailments/example.org/A/B/derived.ttl -> /derived/A/B/derived.ttl As expected? true
```

  - `Test1` demonstrates the bug where the side effects of the 2nd call to `matchURI` is, incorrectly, the same as the result of the 1st call.
The reason is that the 1st call resulted in a `longestRewriteMatch` that, according to the logic of the implementation, constitutes a valid result
even though no actual match has been attempted.

  - `Test2` demonstrates that, if the calls to `matchURI` result in increasing lengths of `longestRewriteMatch`, then the results are correct.
    This is hardly practical.
    
  - `Test3` demonstrates a workaround to the problem by creating a fresh catalog before each call; thereby ensuring the state variables are reset.


- The bug is in the implementation of the `String javax.xml.catalog.GroupEntry.matchURI(String)` method reproduced below:

```java
    public String matchURI(String uri) {
        String match = null;
        for (BaseEntry entry : entries) {
            switch (entry.type) {
                case URI:
                    match = ((UriEntry) entry).match(uri);
                    if (match != null) {
                        isInstantMatch = true;
                        return match;
                    }
                    break;
                case REWRITEURI:
                    match = ((RewriteUri) entry).match(uri, longestRewriteMatch);
                    if (match != null) {
                        rewriteMatch = match;
                        longestRewriteMatch = ((RewriteUri) entry).getURIStartString().length();
                    }
                    break;
                case URISUFFIX:
                    match = ((UriSuffix) entry).match(uri, longestSuffixMatch);
                    if (match != null) {
                        suffixMatch = match;
                        longestSuffixMatch = ((UriSuffix) entry).getURISuffix().length();
                    }
                    break;
                case GROUP:
                    GroupEntry grpEntry = (GroupEntry) entry;
                    match = grpEntry.matchURI(uri);
                    if (grpEntry.isInstantMatch) {
                        //use it if there is a match of the uri type
                        return match;
                    } else if (grpEntry.longestRewriteMatch > longestRewriteMatch) {
                        rewriteMatch = match;
                        longestRewriteMatch = grpEntry.longestRewriteMatch;
                    } else if (grpEntry.longestSuffixMatch > longestSuffixMatch) {
                        suffixMatch = match;
                        longestSuffixMatch = grpEntry.longestSuffixMatch;
                    }
                    break;
            }
        }

        if (longestRewriteMatch > 0) {
            return rewriteMatch;
        } else if (longestSuffixMatch > 0) {
            return suffixMatch;
        }

        //if no single match is found, try delegates
        return matchDelegate(CatalogEntryType.DELEGATEURI, uri);
    }
```

## Suggestion

Insert a call to `javax.xml.catalog.GroupEntry.reset()` before iterating over the catalog entries in the method above.

