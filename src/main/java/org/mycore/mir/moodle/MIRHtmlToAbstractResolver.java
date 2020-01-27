package org.mycore.mir.moodle;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import org.jdom2.Element;
import org.jdom2.transform.JDOMSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRException;
import org.mycore.common.MCRLanguageDetector;
import org.mycore.common.xml.MCRXMLFunctions;
import org.mycore.datamodel.common.MCRDataURL;

public class MIRHtmlToAbstractResolver implements URIResolver {
    @Override
    public Source resolve(String href, String base) throws TransformerException {
        String target = href.substring(href.indexOf(":") + 1);

        try {
            String cleanHTML = MCRXMLFunctions.stripHtml(target);

            final String lang = MCRLanguageDetector.detectLanguage(cleanHTML);
            final Document cleanDocument = Jsoup.parse(target);
            cleanDocument.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

            final String urlEncodedHTML = MCRDataURL
                .build("<?xml version=\"1.0\" encoding=\"utf-8\"?><abstract xml:lang=\"" + lang + "\">" + cleanDocument
                    .html() + "</abstract>", "base64", "text/xml", "UTF-8");
            final String repGroup = UUID.randomUUID().toString().substring(16, 32).replace("-", "");

            final Element abstractElement = new Element("abstract", MCRConstants.MODS_NAMESPACE);
            abstractElement.setAttribute("altRepGroup", repGroup);
            abstractElement.setAttribute("type", "simple", MCRConstants.XLINK_NAMESPACE);
            abstractElement.setAttribute("lang", lang, MCRConstants.XML_NAMESPACE);

            final Element htmlAbstractElement = abstractElement.clone();
            htmlAbstractElement.setAttribute("altFormat", urlEncodedHTML);
            htmlAbstractElement.setAttribute("contentType", "text/xml");

            abstractElement.setText(cleanHTML);

            return new JDOMSource(Stream.of(abstractElement, htmlAbstractElement).collect(Collectors.toList()));
        } catch (MalformedURLException e) {
            throw new MCRException(e);
        }
    }
}
