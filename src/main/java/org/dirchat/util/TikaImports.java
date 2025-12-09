package org.dirchat.util;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.Tika;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class TikaImports {
    public class TikaMetadata extends Metadata {}
    public static class TikaBase extends Tika {}

}
