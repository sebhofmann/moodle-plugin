package org.mycore.mir.moodle;

import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;

import org.jdom2.Element;
import org.jdom2.transform.JDOMSource;
import org.mycore.services.i18n.MCRTranslation;

public class MIRTranslateResolver implements URIResolver {

    public MIRTranslateResolver(){
    }

    @Override
    public Source resolve(String href, String base) {
        String target = href.substring(href.indexOf(":") + 1);

        final Element translations = new Element("translations");
        MCRTranslation.translatePrefix(target).forEach((key, value)-> {
            final Element translation = new Element("translation");
            translation.setAttribute("key",key);
            translation.setText(value);
            translations.addContent(translation);
        });

        return new JDOMSource(translations);
    }
}
