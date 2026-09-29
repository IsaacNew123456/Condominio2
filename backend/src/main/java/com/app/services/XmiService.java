package com.app.services;

import com.app.dto.AttrModel;
import com.app.dto.ClassModel;
import com.app.dto.DiagramModel;
import com.app.dto.RelationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio robusto para interoperabilidad XMI 2.1 (UML Estándar) compatible con Enterprise Architect, StarUML y ArgoUML.
 * Permite exportar DiagramModel a XMI estructurado e importar archivos XML/XMI a DiagramModel
 * extrayendo con precisión clases, atributos, métodos/operaciones y relaciones (asociación, agregación, composición, herencia).
 */
@Service
public class XmiService {

    private static final Logger log = LoggerFactory.getLogger(XmiService.class);
    private static final String XMI_VERSION = "2.1";
    private static final String NS_XMI = "http://schema.omg.org/spec/XMI/2.1";
    private static final String NS_UML = "http://schema.omg.org/spec/UML/2.1";

    /**
     * Exporta un DiagramModel a formato XMI 2.1 estándar.
     * Genera la estructura completa con clases, atributos, métodos, generalizaciones y conectores/asociaciones.
     */
    public String exportToXmi(DiagramModel diagram) {
        if (diagram == null) {
            diagram = new DiagramModel();
        }

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            // Elemento raíz: <xmi:XMI>
            Element root = doc.createElementNS(NS_XMI, "xmi:XMI");
            root.setAttribute("xmi:version", XMI_VERSION);
            root.setAttribute("xmlns:uml", NS_UML);
            root.setAttribute("xmlns:xmi", NS_XMI);
            doc.appendChild(root);

            // Elemento <uml:Model>
            Element model = doc.createElementNS(NS_UML, "uml:Model");
            String safeDiagramName = (diagram.getName() != null && !diagram.getName().isBlank())
                    ? diagram.getName().trim()
                    : "Diagrama";
            String modelId = "EAID_Model_" + cleanId(safeDiagramName);
            model.setAttributeNS(NS_XMI, "xmi:type", "uml:Model");
            model.setAttributeNS(NS_XMI, "xmi:id", modelId);
            model.setAttribute("name", safeDiagramName);
            root.appendChild(model);

            // Mapeo de identificadores y nombres a IDs válidos en XMI
            Map<String, String> classIdToXmiId = new HashMap<>();
            Map<String, ClassModel> classByIdMap = new HashMap<>();
            List<ClassModel> classes = diagram.getClasses() != null ? diagram.getClasses() : new ArrayList<>();

            for (int i = 0; i < classes.size(); i++) {
                ClassModel cls = classes.get(i);
                if (cls == null) {
                    continue;
                }
                int index = i + 1;

                // Asignar identificadores de respaldo si vienen nulos o vacíos
                if (cls.getId() == null || cls.getId().isBlank()) {
                    cls.setId("_cls_" + index);
                }
                if (cls.getName() == null || cls.getName().isBlank()) {
                    cls.setName("Clase_" + index);
                }

                String rawId = cls.getId().trim();
                String xmiClassId = rawId.startsWith("_cls_") ? rawId : "EAID_" + cleanId(rawId);

                classIdToXmiId.put(rawId, xmiClassId);
                classIdToXmiId.put(cls.getName().trim(), xmiClassId);
                classIdToXmiId.put(rawId.toLowerCase(), xmiClassId);
                classIdToXmiId.put(cls.getName().trim().toLowerCase(), xmiClassId);
                classIdToXmiId.put(String.valueOf(i), xmiClassId);
                classIdToXmiId.put(String.valueOf(index), xmiClassId);

                classByIdMap.put(xmiClassId, cls);
                classByIdMap.put(rawId, cls);
            }

            // Clases: <packagedElement xmi:type="uml:Class">
            for (int i = 0; i < classes.size(); i++) {
                ClassModel cls = classes.get(i);
                if (cls == null) {
                    continue;
                }
                int index = i + 1;
                String rawId = (cls.getId() != null && !cls.getId().isBlank()) ? cls.getId().trim() : "_cls_" + index;
                String xmiClassId = classIdToXmiId.get(rawId);
                if (xmiClassId == null) {
                    xmiClassId = rawId.startsWith("_cls_") ? rawId : "EAID_" + cleanId(rawId);
                    classIdToXmiId.put(rawId, xmiClassId);
                }

                Element classElem = doc.createElement("packagedElement");
                classElem.setAttributeNS(NS_XMI, "xmi:type", "uml:Class");
                classElem.setAttributeNS(NS_XMI, "xmi:id", xmiClassId);
                classElem.setAttribute("name", cls.getName() != null && !cls.getName().isBlank() ? cls.getName().trim() : "Clase_" + index);
                classElem.setAttribute("visibility", "public");

                // Atributos: <ownedAttribute xmi:type="uml:Property">
                if (cls.getAttrs() != null) {
                    for (int j = 0; j < cls.getAttrs().size(); j++) {
                        AttrModel attr = cls.getAttrs().get(j);
                        if (attr == null) {
                            continue;
                        }
                        Element attrElem = doc.createElement("ownedAttribute");
                        String attrId = xmiClassId + "_attr_" + (j + 1);
                        attrElem.setAttributeNS(NS_XMI, "xmi:type", "uml:Property");
                        attrElem.setAttributeNS(NS_XMI, "xmi:id", attrId);

                        String attrName = (attr.getName() != null && !attr.getName().isBlank())
                                ? attr.getName().trim()
                                : "attr_" + (j + 1);
                        attrElem.setAttribute("name", attrName);
                        attrElem.setAttribute("visibility", "private");

                        String typeName = (attr.getType() != null && !attr.getType().isBlank())
                                ? attr.getType().trim()
                                : "String";
                        Element typeElem = doc.createElement("type");
                        typeElem.setAttributeNS(NS_XMI, "xmi:type", "uml:PrimitiveType");
                        typeElem.setAttribute("href", "http://schema.omg.org/spec/UML/2.1/uml.xml#" + typeName);
                        typeElem.setAttribute("name", typeName);
                        attrElem.appendChild(typeElem);

                        classElem.appendChild(attrElem);
                    }
                }

                // Métodos / Operaciones: <ownedOperation xmi:type="uml:Operation">
                if (cls.getMethods() != null) {
                    for (int m = 0; m < cls.getMethods().size(); m++) {
                        String methodStr = cls.getMethods().get(m);
                        if (methodStr == null || methodStr.isBlank()) {
                            continue;
                        }
                        Element opElem = doc.createElement("ownedOperation");
                        String opId = xmiClassId + "_op_" + (m + 1);
                        opElem.setAttributeNS(NS_XMI, "xmi:type", "uml:Operation");
                        opElem.setAttributeNS(NS_XMI, "xmi:id", opId);
                        opElem.setAttribute("visibility", "public");

                        ParsedMethod parsed = parseMethodString(methodStr);
                        opElem.setAttribute("name", parsed.name);

                        // Parámetro de retorno si existe y no es void
                        if (parsed.returnType != null && !parsed.returnType.isBlank() && !parsed.returnType.equalsIgnoreCase("void")) {
                            Element retParam = doc.createElement("ownedParameter");
                            retParam.setAttributeNS(NS_XMI, "xmi:type", "uml:Parameter");
                            retParam.setAttributeNS(NS_XMI, "xmi:id", opId + "_ret");
                            retParam.setAttribute("name", "return");
                            retParam.setAttribute("direction", "return");

                            Element retType = doc.createElement("type");
                            retType.setAttributeNS(NS_XMI, "xmi:type", "uml:PrimitiveType");
                            retType.setAttribute("href", "http://schema.omg.org/spec/UML/2.1/uml.xml#" + parsed.returnType);
                            retType.setAttribute("name", parsed.returnType);
                            retParam.appendChild(retType);

                            opElem.appendChild(retParam);
                        }

                        // Parámetros de entrada
                        for (int p = 0; p < parsed.parameters.size(); p++) {
                            MethodParam mp = parsed.parameters.get(p);
                            Element pElem = doc.createElement("ownedParameter");
                            pElem.setAttributeNS(NS_XMI, "xmi:type", "uml:Parameter");
                            pElem.setAttributeNS(NS_XMI, "xmi:id", opId + "_p" + (p + 1));
                            pElem.setAttribute("name", mp.name);

                            if (mp.type != null && !mp.type.isBlank()) {
                                Element pType = doc.createElement("type");
                                pType.setAttributeNS(NS_XMI, "xmi:type", "uml:PrimitiveType");
                                pType.setAttribute("href", "http://schema.omg.org/spec/UML/2.1/uml.xml#" + mp.type);
                                pType.setAttribute("name", mp.type);
                                pElem.appendChild(pType);
                            }
                            opElem.appendChild(pElem);
                        }

                        classElem.appendChild(opElem);
                    }
                }

                // Generalizaciones (Herencia) originadas en esta clase
                if (diagram.getRelations() != null) {
                    for (int k = 0; k < diagram.getRelations().size(); k++) {
                        RelationModel rel = diagram.getRelations().get(k);
                        if (rel == null) continue;

                        if ("inheritance".equalsIgnoreCase(rel.getEffectiveRelationType())) {
                            String srcXmiId = resolveXmiClassId(rel, true, classIdToXmiId);
                            String tgtXmiId = resolveXmiClassId(rel, false, classIdToXmiId);

                            if (srcXmiId != null && srcXmiId.equals(xmiClassId) && tgtXmiId != null) {
                                Element genElem = doc.createElement("generalization");
                                String genId = "EAID_Gen_" + (k + 1);
                                genElem.setAttributeNS(NS_XMI, "xmi:type", "uml:Generalization");
                                genElem.setAttributeNS(NS_XMI, "xmi:id", genId);
                                genElem.setAttribute("general", tgtXmiId);
                                classElem.appendChild(genElem);
                            }
                        }
                    }
                }

                // Conectores que originan en esta clase: <ownedConnector> (No-herencia)
                if (diagram.getRelations() != null) {
                    for (int k = 0; k < diagram.getRelations().size(); k++) {
                        RelationModel rel = diagram.getRelations().get(k);
                        if (rel == null) {
                            continue;
                        }

                        String relType = rel.getEffectiveRelationType();
                        if ("inheritance".equalsIgnoreCase(relType)) {
                            continue; // Ya manejado como generalization
                        }

                        String srcXmiId = resolveXmiClassId(rel, true, classIdToXmiId);
                        String tgtXmiId = resolveXmiClassId(rel, false, classIdToXmiId);

                        // Comprobar si el conector se origina en esta clase y tiene destino válido
                        if (srcXmiId != null && srcXmiId.equals(xmiClassId) && tgtXmiId != null) {
                            Element connectorElem = doc.createElement("ownedConnector");
                            String connId = "EAID_Conn_" + (k + 1);
                            connectorElem.setAttributeNS(NS_XMI, "xmi:type", "uml:Connector");
                            connectorElem.setAttributeNS(NS_XMI, "xmi:id", connId);

                            String fromName = (cls.getName() != null && !cls.getName().isBlank()) ? cls.getName().trim() : "Origen";
                            String toName = rel.getToName();
                            if ((toName == null || toName.isBlank()) && classByIdMap.containsKey(tgtXmiId)) {
                                toName = classByIdMap.get(tgtXmiId).getName();
                            }
                            if (toName == null || toName.isBlank()) {
                                toName = "Destino";
                            }
                            connectorElem.setAttribute("name", "rel_" + fromName + "_" + toName);

                            // Multiplicidades seguras
                            String[] mults = parseMultiplicity(rel);
                            String srcMult = mults[0];
                            String tgtMult = mults[1];

                            // Extremo Origen
                            Element end1 = doc.createElement("end");
                            end1.setAttributeNS(NS_XMI, "xmi:id", connId + "_src");
                            end1.setAttribute("role", xmiClassId);
                            end1.setAttribute("multiplicity", srcMult);

                            if ("aggregation".equalsIgnoreCase(relType)) {
                                end1.setAttribute("aggregation", "shared");
                            } else if ("composition".equalsIgnoreCase(relType)) {
                                end1.setAttribute("aggregation", "composite");
                            }
                            connectorElem.appendChild(end1);

                            // Extremo Destino
                            Element end2 = doc.createElement("end");
                            end2.setAttributeNS(NS_XMI, "xmi:id", connId + "_dst");
                            end2.setAttribute("role", tgtXmiId);
                            end2.setAttribute("multiplicity", tgtMult);
                            connectorElem.appendChild(end2);

                            classElem.appendChild(connectorElem);
                        }
                    }
                }

                model.appendChild(classElem);
            }

            // Asociaciones globales para máxima compatibilidad con Enterprise Architect y herramientas XMI
            if (diagram.getRelations() != null) {
                for (int k = 0; k < diagram.getRelations().size(); k++) {
                    RelationModel rel = diagram.getRelations().get(k);
                    if (rel == null || "inheritance".equalsIgnoreCase(rel.getEffectiveRelationType())) {
                        continue;
                    }

                    String srcId = resolveXmiClassId(rel, true, classIdToXmiId);
                    String tgtId = resolveXmiClassId(rel, false, classIdToXmiId);

                    if (srcId != null && tgtId != null) {
                        Element assoc = doc.createElement("packagedElement");
                        String assocId = "EAID_Assoc_" + (k + 1);
                        assoc.setAttributeNS(NS_XMI, "xmi:type", "uml:Association");
                        assoc.setAttributeNS(NS_XMI, "xmi:id", assocId);

                        String srcName = (rel.getFromName() != null && !rel.getFromName().isBlank())
                                ? rel.getFromName().trim()
                                : (classByIdMap.containsKey(srcId) ? classByIdMap.get(srcId).getName() : "Src");
                        String tgtName = (rel.getToName() != null && !rel.getToName().isBlank())
                                ? rel.getToName().trim()
                                : (classByIdMap.containsKey(tgtId) ? classByIdMap.get(tgtId).getName() : "Dst");
                        assoc.setAttribute("name", srcName + "_" + tgtName);

                        Element member1 = doc.createElement("memberEnd");
                        member1.setAttributeNS(NS_XMI, "xmi:idref", srcId);
                        assoc.appendChild(member1);

                        Element member2 = doc.createElement("memberEnd");
                        member2.setAttributeNS(NS_XMI, "xmi:idref", tgtId);
                        assoc.appendChild(member2);

                        model.appendChild(assoc);
                    }
                }
            }

            // Convertir DOM a XML String con formato
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();

        } catch (Exception e) {
            log.error("Error al exportar diagrama a formato XMI: {}", e.getMessage(), e);
            throw new RuntimeException("Error al exportar diagrama a formato XMI: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    /**
     * Importa y parsea un archivo o texto XML/XMI a DiagramModel.
     */
    public DiagramModel importFromXmi(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new IllegalArgumentException("El contenido XML/XMI no puede estar vacío");
        }
        return importFromXmi(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Importa y parsea un InputStream con XML/XMI a DiagramModel en memoria.
     * Soporta namespaces estándar de UML y formatos de Enterprise Architect, StarUML, MagicDraw, etc.
     */
    public DiagramModel importFromXmi(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("El flujo de entrada InputStream no puede ser nulo");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Configuración de seguridad XXE tolerante a DTDs declaradas en archivos estándar
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            try {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
                // Características opcionales dependiendo del parser XML de la JVM
            }
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();

            DiagramModel diagram = new DiagramModel();
            diagram.setName("Diagrama Importado");

            // Extraer nombre del modelo si está presente
            NodeList modelNodes = doc.getElementsByTagNameNS("*", "Model");
            if (modelNodes.getLength() == 0) {
                modelNodes = doc.getElementsByTagName("uml:Model");
            }
            if (modelNodes.getLength() == 0) {
                modelNodes = doc.getElementsByTagName("Model");
            }
            if (modelNodes.getLength() > 0) {
                Element modelElem = (Element) modelNodes.item(0);
                String nameAttr = getAttrAny(modelElem, "name");
                if (nameAttr != null && !nameAttr.isBlank()) {
                    diagram.setName(nameAttr.trim());
                }
            }

            Map<String, ClassModel> classMap = new HashMap<>(); // ID o xmi:id -> ClassModel
            Map<String, String> idToNameMap = new HashMap<>();
            List<ClassModel> classes = new ArrayList<>();
            List<RelationModel> relations = new ArrayList<>();

            // Buscar clases en el documento:
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Node node = allElements.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element elem = (Element) node;

                if (isClassElement(elem)) {
                    String classId = getAttrAny(elem, "xmi:id", "id", "xmiId");
                    String className = getAttrAny(elem, "name");

                    if (className == null || className.isBlank()) {
                        className = "Clase_" + (classes.size() + 1);
                    }
                    if (classId == null || classId.isBlank()) {
                        classId = "cls_" + UUID.randomUUID().toString().substring(0, 8);
                    }

                    ClassModel classModel = new ClassModel();
                    classModel.setId(classId);
                    classModel.setName(className.trim());
                    classModel.setAttrs(new ArrayList<>());
                    classModel.setMethods(new ArrayList<>());
                    classModel.setX(40.0 + (classes.size() % 3) * 260.0);
                    classModel.setY(60.0 + Math.floor(classes.size() / 3.0) * 220.0);

                    // Extraer atributos, operaciones y generalizaciones internas
                    NodeList children = elem.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        Node child = children.item(j);
                        if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                        Element childElem = (Element) child;

                        if (isAttributeElement(childElem)) {
                            String attrName = getAttrAny(childElem, "name");
                            if (attrName != null && !attrName.isBlank()) {
                                String attrType = extractAttributeType(childElem);
                                AttrModel attrModel = new AttrModel();
                                attrModel.setName(attrName.trim());
                                attrModel.setType(attrType != null && !attrType.isBlank() ? attrType.trim() : "String");
                                if ("true".equalsIgnoreCase(getAttrAny(childElem, "isPrimary", "isId"))
                                        || attrName.equalsIgnoreCase("id") || attrName.equalsIgnoreCase("codigo")) {
                                    attrModel.setIsPrimary(true);
                                }
                                classModel.getAttrs().add(attrModel);
                            }
                        } else if (isOperationElement(childElem)) {
                            String opSignature = extractOperationSignature(childElem);
                            if (opSignature != null && !opSignature.isBlank()) {
                                classModel.getMethods().add(opSignature);
                            }
                        } else if (isGeneralizationElement(childElem)) {
                            String parentId = getAttrAny(childElem, "general", "parent", "target", "xmi:idref");
                            if (parentId != null && !parentId.isBlank()) {
                                RelationModel inhRel = new RelationModel();
                                inhRel.setId("rel_gen_" + classId + "_" + parentId);
                                inhRel.setFromId(classId);
                                inhRel.setToId(parentId);
                                inhRel.setSourceId(classId);
                                inhRel.setTargetId(parentId);
                                inhRel.setFromName(classModel.getName());
                                inhRel.setRelationType("inheritance");
                                inhRel.setMult("");
                                inhRel.setSourceMultiplicity("");
                                inhRel.setTargetMultiplicity("");
                                if (!containsRelation(relations, inhRel)) {
                                    relations.add(inhRel);
                                }
                            }
                        }
                    }

                    classMap.put(classId, classModel);
                    idToNameMap.put(classId, classModel.getName());
                    classMap.put(classModel.getName().toLowerCase(), classModel);
                    classes.add(classModel);
                }
            }

            // Extraer conectores y relaciones:
            // 1) <ownedConnector> o <connector> dentro de elementos
            // 2) <packagedElement xmi:type="uml:Association"> o <uml:Association>
            // 3) Generalizaciones globales (<packagedElement xmi:type="uml:Generalization">)
            for (int i = 0; i < allElements.getLength(); i++) {
                Node node = allElements.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element elem = (Element) node;

                if (isConnectorElement(elem)) {
                    RelationModel rel = parseConnectorElement(elem, classMap, idToNameMap);
                    if (rel != null && !containsRelation(relations, rel)) {
                        relations.add(rel);
                    }
                } else if (isAssociationElement(elem)) {
                    RelationModel rel = parseAssociationElement(elem, classMap, idToNameMap);
                    if (rel != null && !containsRelation(relations, rel)) {
                        relations.add(rel);
                    }
                } else if (isGeneralizationElement(elem)) {
                    String childId = getAttrAny(elem, "specific", "child", "source");
                    String parentId = getAttrAny(elem, "general", "parent", "target");
                    if (childId != null && parentId != null) {
                        RelationModel inhRel = new RelationModel();
                        inhRel.setId("rel_gen_" + childId + "_" + parentId);
                        inhRel.setFromId(childId);
                        inhRel.setToId(parentId);
                        inhRel.setSourceId(childId);
                        inhRel.setTargetId(parentId);
                        inhRel.setFromName(idToNameMap.getOrDefault(childId, childId));
                        inhRel.setToName(idToNameMap.getOrDefault(parentId, parentId));
                        inhRel.setRelationType("inheritance");
                        inhRel.setMult("");
                        inhRel.setSourceMultiplicity("");
                        inhRel.setTargetMultiplicity("");
                        if (!containsRelation(relations, inhRel)) {
                            relations.add(inhRel);
                        }
                    }
                }
            }

            // Resolver nombres de origen/destino pendientes en relaciones
            for (RelationModel rel : relations) {
                if ((rel.getFromName() == null || rel.getFromName().isBlank()) && rel.getFromId() != null) {
                    ClassModel c = classMap.get(rel.getFromId());
                    if (c != null) rel.setFromName(c.getName());
                }
                if ((rel.getToName() == null || rel.getToName().isBlank()) && rel.getToId() != null) {
                    ClassModel c = classMap.get(rel.getToId());
                    if (c != null) rel.setToName(c.getName());
                }
            }

            diagram.setClasses(classes);
            diagram.setRelations(relations);
            return diagram;

        } catch (Exception e) {
            log.error("Error al parsear el archivo XMI/XML: {}", e.getMessage(), e);
            throw new RuntimeException("Error al parsear el archivo XMI/XML: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    private boolean isClassElement(Element elem) {
        if (elem == null) return false;
        String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
        String xmiType = getAttrAny(elem, "xmi:type", "type");

        if ("packagedElement".equalsIgnoreCase(tagName) || "element".equalsIgnoreCase(tagName)) {
            return xmiType != null && (xmiType.equalsIgnoreCase("uml:Class") || xmiType.equalsIgnoreCase("Class"));
        }
        return "Class".equalsIgnoreCase(tagName)
                || "uml:Class".equalsIgnoreCase(elem.getTagName())
                || "UML:Class".equalsIgnoreCase(elem.getTagName());
    }

    private boolean isAttributeElement(Element elem) {
        if (elem == null) return false;
        String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
        String xmiType = getAttrAny(elem, "xmi:type", "type");

        if ("ownedAttribute".equalsIgnoreCase(tagName) || "attribute".equalsIgnoreCase(tagName) || "Attribute".equalsIgnoreCase(tagName)) {
            return true;
        }
        return xmiType != null && (xmiType.equalsIgnoreCase("uml:Property") || xmiType.equalsIgnoreCase("Property"));
    }

    private boolean isOperationElement(Element elem) {
        if (elem == null) return false;
        String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
        String xmiType = getAttrAny(elem, "xmi:type", "type");

        if ("ownedOperation".equalsIgnoreCase(tagName) || "operation".equalsIgnoreCase(tagName) || "Operation".equalsIgnoreCase(tagName) || "UML:Operation".equalsIgnoreCase(tagName)) {
            return true;
        }
        return xmiType != null && (xmiType.equalsIgnoreCase("uml:Operation") || xmiType.equalsIgnoreCase("Operation"));
    }

    private boolean isGeneralizationElement(Element elem) {
        if (elem == null) return false;
        String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
        String xmiType = getAttrAny(elem, "xmi:type", "type");

        if ("generalization".equalsIgnoreCase(tagName) || "Generalization".equalsIgnoreCase(tagName) || "UML:Generalization".equalsIgnoreCase(tagName)) {
            return true;
        }
        return xmiType != null && (xmiType.equalsIgnoreCase("uml:Generalization") || xmiType.equalsIgnoreCase("Generalization"));
    }

    private boolean isConnectorElement(Element elem) {
        if (elem == null) return false;
        String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
        String xmiType = getAttrAny(elem, "xmi:type", "type");
        return "ownedConnector".equalsIgnoreCase(tagName) || "connector".equalsIgnoreCase(tagName)
                || (xmiType != null && xmiType.toLowerCase().contains("connector"));
    }

    private boolean isAssociationElement(Element elem) {
        if (elem == null) return false;
        String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
        String xmiType = getAttrAny(elem, "xmi:type", "type");

        if ("packagedElement".equalsIgnoreCase(tagName)) {
            return xmiType != null && (xmiType.equalsIgnoreCase("uml:Association") || xmiType.equalsIgnoreCase("Association"));
        }
        return "Association".equalsIgnoreCase(tagName) || "uml:Association".equalsIgnoreCase(elem.getTagName()) || "UML:Association".equalsIgnoreCase(elem.getTagName());
    }

    /**
     * Extrae la firma de una operación/método desde un elemento <ownedOperation>.
     * Retorna formatos estándar como "calcularTotal(descuento: Double): Double" o "procesar()".
     */
    private String extractOperationSignature(Element opElem) {
        if (opElem == null) return null;
        String name = getAttrAny(opElem, "name");
        if (name == null || name.isBlank()) return null;
        name = name.trim();

        // Si el nombre ya contiene paréntesis completos (ej. "calcularTotal()"), respetarlo
        if (name.contains("(") && name.contains(")")) {
            return name;
        }

        List<String> paramStrings = new ArrayList<>();
        String returnType = null;

        NodeList children = opElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element childElem = (Element) child;
            String tagName = childElem.getLocalName() != null ? childElem.getLocalName() : childElem.getTagName();

            if ("ownedParameter".equalsIgnoreCase(tagName) || "parameter".equalsIgnoreCase(tagName) || "Parameter".equalsIgnoreCase(tagName)) {
                String direction = getAttrAny(childElem, "direction");
                String pName = getAttrAny(childElem, "name");
                String pType = extractAttributeType(childElem);

                if ("return".equalsIgnoreCase(direction) || "return".equalsIgnoreCase(pName)) {
                    if (pType != null && !pType.isBlank() && !pType.equalsIgnoreCase("void")) {
                        returnType = pType;
                    }
                } else if (pName != null && !pName.isBlank()) {
                    paramStrings.add(pName.trim() + (pType != null && !pType.isBlank() ? ": " + pType.trim() : ""));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(name).append("(").append(String.join(", ", paramStrings)).append(")");
        if (returnType != null && !returnType.isBlank()) {
            sb.append(": ").append(returnType);
        }
        return sb.toString();
    }

    private String extractAttributeType(Element attrElem) {
        if (attrElem == null) return "String";

        // 1) Atributo directo type="String"
        String typeAttr = getAttrAny(attrElem, "type");
        if (typeAttr != null && !typeAttr.isBlank() && !typeAttr.startsWith("uml:") && !typeAttr.startsWith("EAID_")) {
            return cleanTypeName(typeAttr);
        }

        // 2) Elemento hijo <type> o <dataType>
        NodeList typeNodes = attrElem.getElementsByTagName("*");
        for (int i = 0; i < typeNodes.getLength(); i++) {
            Node node = typeNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element typeElem = (Element) node;
            String nodeName = typeElem.getLocalName() != null ? typeElem.getLocalName() : typeElem.getTagName();

            if ("type".equalsIgnoreCase(nodeName) || "dataType".equalsIgnoreCase(nodeName) || "primitiveType".equalsIgnoreCase(nodeName)) {
                String name = getAttrAny(typeElem, "name");
                if (name != null && !name.isBlank()) return cleanTypeName(name);

                String href = getAttrAny(typeElem, "href");
                if (href != null && !href.isBlank()) {
                    int hashIdx = href.lastIndexOf('#');
                    if (hashIdx >= 0 && hashIdx < href.length() - 1) {
                        return cleanTypeName(href.substring(hashIdx + 1));
                    }
                }
            }
        }
        return "String";
    }

    private RelationModel parseConnectorElement(Element connElem, Map<String, ClassModel> classMap, Map<String, String> idToNameMap) {
        if (connElem == null) return null;

        NodeList ends = connElem.getElementsByTagName("*");
        List<Element> endElements = new ArrayList<>();
        for (int i = 0; i < ends.getLength(); i++) {
            Node n = ends.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) n;
                String local = e.getLocalName() != null ? e.getLocalName() : e.getTagName();
                if ("end".equalsIgnoreCase(local) || "participant".equalsIgnoreCase(local)) {
                    endElements.add(e);
                }
            }
        }

        if (endElements.size() >= 2) {
            Element end1 = endElements.get(0);
            Element end2 = endElements.get(1);

            String role1 = getAttrAny(end1, "role", "roleId", "xmi:idref", "participant");
            String role2 = getAttrAny(end2, "role", "roleId", "xmi:idref", "participant");

            String mult1 = getAttrAny(end1, "multiplicity", "mult", "lower", "upper");
            String mult2 = getAttrAny(end2, "multiplicity", "mult", "lower", "upper");

            String agg1 = getAttrAny(end1, "aggregation");
            String agg2 = getAttrAny(end2, "aggregation");
            String relationType = "association";
            if ("composite".equalsIgnoreCase(agg1) || "composite".equalsIgnoreCase(agg2)) {
                relationType = "composition";
            } else if ("shared".equalsIgnoreCase(agg1) || "shared".equalsIgnoreCase(agg2)) {
                relationType = "aggregation";
            }

            RelationModel rel = createRelation(role1, role2, mult1, mult2, classMap, idToNameMap);
            if (rel != null) {
                rel.setRelationType(relationType);
            }
            return rel;
        }
        return null;
    }

    private RelationModel parseAssociationElement(Element assocElem, Map<String, ClassModel> classMap, Map<String, String> idToNameMap) {
        if (assocElem == null) return null;

        NodeList members = assocElem.getElementsByTagName("*");
        List<String> memberIds = new ArrayList<>();
        List<String> mults = new ArrayList<>();
        String relationType = "association";

        for (int i = 0; i < members.getLength(); i++) {
            Node n = members.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) n;
                String local = e.getLocalName() != null ? e.getLocalName() : e.getTagName();
                if ("memberEnd".equalsIgnoreCase(local) || "ownedEnd".equalsIgnoreCase(local) || "connection".equalsIgnoreCase(local)) {
                    String idref = getAttrAny(e, "xmi:idref", "idref", "role", "type");
                    if (idref != null && ((classMap != null && classMap.containsKey(idref)) || (idToNameMap != null && idToNameMap.containsKey(idref)))) {
                        memberIds.add(idref);
                        String m = getAttrAny(e, "multiplicity", "mult");
                        mults.add(m != null ? m : "*");
                    }
                    String agg = getAttrAny(e, "aggregation");
                    if ("composite".equalsIgnoreCase(agg)) {
                        relationType = "composition";
                    } else if ("shared".equalsIgnoreCase(agg)) {
                        relationType = "aggregation";
                    }
                }
            }
        }

        if (memberIds.size() >= 2) {
            String m1 = mults.size() > 0 ? mults.get(0) : "1";
            String m2 = mults.size() > 1 ? mults.get(1) : "*";
            RelationModel rel = createRelation(memberIds.get(0), memberIds.get(1), m1, m2, classMap, idToNameMap);
            if (rel != null) {
                rel.setRelationType(relationType);
            }
            return rel;
        }
        return null;
    }

    private RelationModel createRelation(String fromId, String toId, String mult1, String mult2,
                                         Map<String, ClassModel> classMap, Map<String, String> idToNameMap) {
        if (fromId == null || toId == null) return null;

        ClassModel fromClass = classMap != null ? classMap.get(fromId) : null;
        ClassModel toClass = classMap != null ? classMap.get(toId) : null;

        String fromName = fromClass != null ? fromClass.getName() : (idToNameMap != null ? idToNameMap.getOrDefault(fromId, fromId) : fromId);
        String toName = toClass != null ? toClass.getName() : (idToNameMap != null ? idToNameMap.getOrDefault(toId, toId) : toId);

        String sMult = (mult1 != null && !mult1.isBlank()) ? mult1.trim() : "1";
        String tMult = (mult2 != null && !mult2.isBlank()) ? mult2.trim() : "*";

        String multiplicity = sMult + ".." + tMult;
        if (sMult.equals("1") && tMult.equals("*")) {
            multiplicity = "1..*";
        }

        RelationModel rel = new RelationModel();
        rel.setId("rel_" + cleanId(fromName) + "_" + cleanId(toName) + "_" + UUID.randomUUID().toString().substring(0, 4));
        rel.setFromId(fromClass != null ? fromClass.getId() : fromId);
        rel.setToId(toClass != null ? toClass.getId() : toId);
        rel.setSourceId(rel.getFromId());
        rel.setTargetId(rel.getToId());
        rel.setFromName(fromName);
        rel.setToName(toName);
        rel.setSourceMultiplicity(sMult);
        rel.setTargetMultiplicity(tMult);
        rel.setMult(multiplicity);
        rel.setRelationType("association");
        return rel;
    }

    private String resolveXmiClassId(RelationModel rel, boolean isSource, Map<String, String> classIdToXmiId) {
        if (rel == null || classIdToXmiId == null) {
            return null;
        }

        String id = isSource
                ? (rel.getFromId() != null && !rel.getFromId().isBlank() ? rel.getFromId().trim() : rel.getSourceId())
                : (rel.getToId() != null && !rel.getToId().isBlank() ? rel.getToId().trim() : rel.getTargetId());

        if (id != null && !id.isBlank()) {
            String trimmed = id.trim();
            String xmiId = classIdToXmiId.get(trimmed);
            if (xmiId != null) return xmiId;
            xmiId = classIdToXmiId.get(trimmed.toLowerCase());
            if (xmiId != null) return xmiId;
        }

        String name = isSource ? rel.getFromName() : rel.getToName();
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            String xmiId = classIdToXmiId.get(trimmed);
            if (xmiId != null) return xmiId;
            xmiId = classIdToXmiId.get(trimmed.toLowerCase());
            if (xmiId != null) return xmiId;
        }

        return null;
    }

    private String[] parseMultiplicity(RelationModel rel) {
        if (rel == null) {
            return new String[]{"1", "*"};
        }

        String srcMult = (rel.getSourceMultiplicity() != null && !rel.getSourceMultiplicity().isBlank())
                ? rel.getSourceMultiplicity().trim()
                : null;
        String tgtMult = (rel.getTargetMultiplicity() != null && !rel.getTargetMultiplicity().isBlank())
                ? rel.getTargetMultiplicity().trim()
                : null;

        if (srcMult != null && tgtMult != null) {
            return new String[]{srcMult, tgtMult};
        }

        String mult = rel.getMult() != null ? rel.getMult().trim() : "";
        if (mult.contains("..")) {
            String[] parts = mult.split("\\.\\.");
            if (srcMult == null) srcMult = (parts.length > 0 && !parts[0].isBlank()) ? parts[0].trim() : "1";
            if (tgtMult == null) tgtMult = (parts.length > 1 && !parts[1].isBlank()) ? parts[1].trim() : "*";
        } else if (mult.contains(":") || mult.contains("-")) {
            String[] parts = mult.split("[:\\-]");
            if (srcMult == null) srcMult = (parts.length > 0 && !parts[0].isBlank()) ? parts[0].trim() : "1";
            if (tgtMult == null) tgtMult = (parts.length > 1 && !parts[1].isBlank()) ? parts[1].trim() : "*";
        } else if (mult.toLowerCase().contains("muchos") || mult.equals("*") || mult.equalsIgnoreCase("n")) {
            if (srcMult == null) srcMult = "1";
            if (tgtMult == null) tgtMult = "*";
        } else if (mult.equalsIgnoreCase("1 a 1") || mult.equalsIgnoreCase("uno a uno")) {
            if (srcMult == null) srcMult = "1";
            if (tgtMult == null) tgtMult = "1";
        } else if (!mult.isBlank()) {
            if (srcMult == null) srcMult = "1";
            if (tgtMult == null) tgtMult = mult;
        }

        if (srcMult == null || srcMult.isBlank()) srcMult = "1";
        if (tgtMult == null || tgtMult.isBlank()) tgtMult = "*";

        return new String[]{srcMult, tgtMult};
    }

    private boolean containsRelation(List<RelationModel> list, RelationModel rel) {
        if (list == null || rel == null) {
            return false;
        }
        return list.stream().anyMatch(r -> {
            if (r == null) return false;
            boolean matchIds = (r.getFromId() != null && rel.getFromId() != null && r.getFromId().equals(rel.getFromId()))
                    && (r.getToId() != null && rel.getToId() != null && r.getToId().equals(rel.getToId()));
            boolean matchNames = (r.getFromName() != null && rel.getFromName() != null && r.getFromName().equalsIgnoreCase(rel.getFromName()))
                    && (r.getToName() != null && rel.getToName() != null && r.getToName().equalsIgnoreCase(rel.getToName()));
            return (matchIds || matchNames) && r.getEffectiveRelationType().equalsIgnoreCase(rel.getEffectiveRelationType());
        });
    }

    private String cleanId(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String cleanTypeName(String type) {
        if (type == null || type.isBlank()) return "String";
        type = type.trim();
        if (type.startsWith("EAJava_")) {
            type = type.substring(7);
        }
        if (type.equalsIgnoreCase("int") || type.equalsIgnoreCase("integer") || type.equalsIgnoreCase("number")) return "Integer";
        if (type.equalsIgnoreCase("long") || type.equalsIgnoreCase("bigint")) return "Long";
        if (type.equalsIgnoreCase("varchar") || type.equalsIgnoreCase("text") || type.equalsIgnoreCase("char") || type.equalsIgnoreCase("string")) return "String";
        if (type.equalsIgnoreCase("date") || type.equalsIgnoreCase("localdate")) return "LocalDate";
        if (type.equalsIgnoreCase("datetime") || type.equalsIgnoreCase("localdatetime") || type.equalsIgnoreCase("timestamp")) return "LocalDateTime";
        if (type.equalsIgnoreCase("double") || type.equalsIgnoreCase("float")) return "Double";
        if (type.equalsIgnoreCase("numeric") || type.equalsIgnoreCase("decimal") || type.equalsIgnoreCase("bigdecimal")) return "BigDecimal";
        if (type.equalsIgnoreCase("boolean") || type.equalsIgnoreCase("bool")) return "Boolean";
        return type;
    }

    private String getAttrAny(Element elem, String... attrNames) {
        if (elem == null || attrNames == null) return null;
        for (String name : attrNames) {
            if (name == null || name.isBlank()) continue;
            if (elem.hasAttribute(name)) {
                String val = elem.getAttribute(name);
                if (val != null && !val.isBlank()) return val.trim();
            }
            if (elem.hasAttributeNS(NS_XMI, name)) {
                String val = elem.getAttributeNS(NS_XMI, name);
                if (val != null && !val.isBlank()) return val.trim();
            }
            if (name.contains(":")) {
                String local = name.substring(name.indexOf(':') + 1);
                if (elem.hasAttribute(local)) {
                    String val = elem.getAttribute(local);
                    if (val != null && !val.isBlank()) return val.trim();
                }
            }
        }
        return null;
    }

    /**
     * Parsea un string de método (ej. "calcularTotal(descuento: Double): Double" o "login()")
     * en componentes estructurados para generar XML XMI válido.
     */
    private ParsedMethod parseMethodString(String raw) {
        ParsedMethod result = new ParsedMethod();
        if (raw == null || raw.isBlank()) {
            result.name = "metodo";
            return result;
        }
        String trimmed = raw.trim();

        // Extraer tipo de retorno si está al final con : Tipo
        int lastColon = trimmed.lastIndexOf(':');
        int lastParen = trimmed.lastIndexOf(')');
        if (lastColon > lastParen && lastParen >= 0) {
            result.returnType = cleanTypeName(trimmed.substring(lastColon + 1).trim());
            trimmed = trimmed.substring(0, lastColon).trim();
        }

        // Extraer nombre y parámetros
        int firstParen = trimmed.indexOf('(');
        if (firstParen >= 0 && lastParen > firstParen) {
            result.name = trimmed.substring(0, firstParen).trim();
            String paramsInside = trimmed.substring(firstParen + 1, lastParen).trim();
            if (!paramsInside.isBlank()) {
                String[] pTokens = paramsInside.split(",");
                for (String pTok : pTokens) {
                    pTok = pTok.trim();
                    if (pTok.isBlank()) continue;
                    MethodParam mp = new MethodParam();
                    if (pTok.contains(":")) {
                        String[] parts = pTok.split(":", 2);
                        mp.name = parts[0].trim();
                        mp.type = cleanTypeName(parts[1].trim());
                    } else {
                        mp.name = pTok;
                        mp.type = "String";
                    }
                    result.parameters.add(mp);
                }
            }
        } else {
            result.name = trimmed.replaceAll("[^a-zA-Z0-9_]", "_");
        }

        if (result.name.isBlank()) {
            result.name = "operacion";
        }
        return result;
    }

    private static class ParsedMethod {
        String name;
        String returnType;
        List<MethodParam> parameters = new ArrayList<>();
    }

    private static class MethodParam {
        String name;
        String type;
    }
}
