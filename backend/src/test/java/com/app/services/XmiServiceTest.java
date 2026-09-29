package com.app.services;

import com.app.dto.AttrModel;
import com.app.dto.ClassModel;
import com.app.dto.DiagramModel;
import com.app.dto.RelationModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmiServiceTest {

    private DiagramModel createSampleDiagram() {
        DiagramModel model = new DiagramModel();
        model.setName("ECommerceModel");

        ClassModel cliente = new ClassModel();
        cliente.setId("cls_cliente_1");
        cliente.setName("Cliente");
        AttrModel email = new AttrModel();
        email.setName("email");
        email.setType("String");
        cliente.getAttrs().add(email);

        AttrModel telefono = new AttrModel();
        telefono.setName("telefono");
        telefono.setType("String");
        cliente.getAttrs().add(telefono);

        ClassModel pedido = new ClassModel();
        pedido.setId("cls_pedido_2");
        pedido.setName("Pedido");
        AttrModel total = new AttrModel();
        total.setName("total");
        total.setType("BigDecimal");
        pedido.getAttrs().add(total);

        model.getClasses().add(cliente);
        model.getClasses().add(pedido);

        RelationModel rel = new RelationModel();
        rel.setFromId("cls_cliente_1");
        rel.setToId("cls_pedido_2");
        rel.setFromName("Cliente");
        rel.setToName("Pedido");
        rel.setMult("1..*");
        model.getRelations().add(rel);

        return model;
    }

    @Test
    void exportToXmiGeneratesValidXmi21Structure() {
        XmiService xmiService = new XmiService();
        DiagramModel model = createSampleDiagram();

        String xmi = xmiService.exportToXmi(model);
        assertNotNull(xmi);
        assertTrue(xmi.contains("<xmi:XMI"));
        assertTrue(xmi.contains("xmi:version=\"2.1\""));
        assertTrue(xmi.contains("<uml:Model"));
        assertTrue(xmi.contains("name=\"ECommerceModel\""));
        assertTrue(xmi.contains("xmi:type=\"uml:Class\""));
        assertTrue(xmi.contains("name=\"Cliente\""));
        assertTrue(xmi.contains("name=\"Pedido\""));
        assertTrue(xmi.contains("<ownedAttribute"));
        assertTrue(xmi.contains("name=\"email\""));
        assertTrue(xmi.contains("name=\"total\""));
        assertTrue(xmi.contains("<ownedConnector"));
    }

    @Test
    void importFromXmiParsesExportedXmiBackToDiagramModel() {
        XmiService xmiService = new XmiService();
        DiagramModel original = createSampleDiagram();

        String xmi = xmiService.exportToXmi(original);
        DiagramModel imported = xmiService.importFromXmi(xmi);

        assertNotNull(imported);
        assertEquals("ECommerceModel", imported.getName());
        assertEquals(2, imported.getClasses().size());

        ClassModel cliente = imported.getClasses().stream()
                .filter(c -> c.getName().equals("Cliente"))
                .findFirst()
                .orElse(null);
        assertNotNull(cliente);
        assertEquals(2, cliente.getAttrs().size());
        assertEquals("email", cliente.getAttrs().get(0).getName());
        assertEquals("String", cliente.getAttrs().get(0).getType());

        ClassModel pedido = imported.getClasses().stream()
                .filter(c -> c.getName().equals("Pedido"))
                .findFirst()
                .orElse(null);
        assertNotNull(pedido);
        assertEquals(1, pedido.getAttrs().size());
        assertEquals("total", pedido.getAttrs().get(0).getName());
        assertEquals("BigDecimal", pedido.getAttrs().get(0).getType());

        assertFalse(imported.getRelations().isEmpty());
        RelationModel rel = imported.getRelations().get(0);
        assertTrue((rel.getFromName().equals("Cliente") && rel.getToName().equals("Pedido"))
                || (rel.getFromName().equals("Pedido") && rel.getToName().equals("Cliente")));
    }

    @Test
    void importFromEnterpriseArchitectXmiSample() {
        XmiService xmiService = new XmiService();
        String eaXmi = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmi:version="2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1">
                  <uml:Model xmi:type="uml:Model" name="EA_Sample_Model" xmi:id="MX_1">
                    <packagedElement xmi:type="uml:Class" xmi:id="EAID_C1" name="Factura">
                      <ownedAttribute xmi:type="uml:Property" xmi:id="EAID_A1" name="numero" visibility="private">
                        <type xmi:type="uml:PrimitiveType" href="http://schema.omg.org/spec/UML/2.1/uml.xml#Integer"/>
                      </ownedAttribute>
                      <ownedAttribute xmi:type="uml:Property" xmi:id="EAID_A2" name="montoTotal" visibility="private">
                        <type xmi:type="uml:PrimitiveType" href="http://schema.omg.org/spec/UML/2.1/uml.xml#BigDecimal"/>
                      </ownedAttribute>
                    </packagedElement>
                    <packagedElement xmi:type="uml:Class" xmi:id="EAID_C2" name="ItemFactura">
                      <ownedAttribute xmi:type="uml:Property" xmi:id="EAID_A3" name="cantidad" visibility="private">
                        <type xmi:type="uml:PrimitiveType" href="http://schema.omg.org/spec/UML/2.1/uml.xml#Integer"/>
                      </ownedAttribute>
                      <ownedConnector xmi:type="uml:Connector" xmi:id="EAID_CN1" name="Factura_Items">
                        <end role="EAID_C1" multiplicity="1"/>
                        <end role="EAID_C2" multiplicity="*"/>
                      </ownedConnector>
                    </packagedElement>
                  </uml:Model>
                </xmi:XMI>
                """;

        DiagramModel diagram = xmiService.importFromXmi(eaXmi);
        assertNotNull(diagram);
        assertEquals("EA_Sample_Model", diagram.getName());
        assertEquals(2, diagram.getClasses().size());

        ClassModel factura = diagram.getClasses().stream().filter(c -> c.getName().equals("Factura")).findFirst().orElse(null);
        assertNotNull(factura);
        assertEquals(2, factura.getAttrs().size());
        assertEquals("numero", factura.getAttrs().get(0).getName());
        assertEquals("Long", factura.getAttrs().get(0).getType());

        assertEquals(1, diagram.getRelations().size());
        assertEquals("1..*", diagram.getRelations().get(0).getMult());
    }

    @Test
    void exportToXmiWithNullIdsAndNamesAssignsFallbackIdentifiers() {
        XmiService xmiService = new XmiService();
        DiagramModel model = new DiagramModel();
        model.setName(null);

        ClassModel c1 = new ClassModel();
        c1.setId(null);
        c1.setName(null);
        AttrModel a1 = new AttrModel();
        a1.setName(null);
        a1.setType(null);
        c1.getAttrs().add(a1);

        ClassModel c2 = new ClassModel();
        c2.setId("");
        c2.setName("  ");

        model.getClasses().add(c1);
        model.getClasses().add(c2);

        String xmi = xmiService.exportToXmi(model);
        assertNotNull(xmi);
        assertTrue(xmi.contains("xmi:id=\"_cls_1\""));
        assertTrue(xmi.contains("name=\"Clase_1\""));
        assertTrue(xmi.contains("xmi:id=\"_cls_2\""));
        assertTrue(xmi.contains("name=\"Clase_2\""));
        assertTrue(xmi.contains("attr_1"));
        assertTrue(xmi.contains("String"));
    }

    @Test
    void exportToXmiWithRelationsHavingSourceTargetIdAndWordMultiplicity() {
        XmiService xmiService = new XmiService();
        DiagramModel model = new DiagramModel();
        model.setName("Empresa");

        ClassModel departamento = new ClassModel();
        departamento.setId("d1");
        departamento.setName("Departamento");

        ClassModel empleado = new ClassModel();
        empleado.setId("e1");
        empleado.setName("Empleado");

        model.getClasses().add(departamento);
        model.getClasses().add(empleado);

        // Relación donde fromId/toId son nulos, pero sourceId/targetId están presentes,
        // y la multiplicidad es "1 a muchos"
        RelationModel rel = new RelationModel();
        rel.setFromId(null);
        rel.setToId(null);
        rel.setSourceId("d1");
        rel.setTargetId("e1");
        rel.setFromName(null);
        rel.setToName(null);
        rel.setMult("1 a muchos");
        rel.setRelationType("composition");
        model.getRelations().add(rel);

        String xmi = xmiService.exportToXmi(model);
        assertNotNull(xmi);
        assertTrue(xmi.contains("<ownedConnector"));
        assertTrue(xmi.contains("multiplicity=\"1\""));
        assertTrue(xmi.contains("multiplicity=\"*\""));
        assertTrue(xmi.contains("aggregation=\"composite\""));
        assertTrue(xmi.contains("xmi:type=\"uml:Association\""));
    }

    @Test
    void exportAndImportWithMethodsAndInheritance() {
        XmiService xmiService = new XmiService();
        DiagramModel model = new DiagramModel();
        model.setName("SistemaBancario");

        ClassModel cuenta = new ClassModel();
        cuenta.setId("cls_cuenta");
        cuenta.setName("Cuenta");
        cuenta.getAttrs().add(new AttrModel("numero", "String"));
        cuenta.getAttrs().add(new AttrModel("saldo", "BigDecimal"));
        cuenta.getMethods().add("depositar(monto: BigDecimal): Boolean");
        cuenta.getMethods().add("consultarSaldo(): BigDecimal");

        ClassModel cuentaAhorros = new ClassModel();
        cuentaAhorros.setId("cls_ahorros");
        cuentaAhorros.setName("CuentaAhorros");
        cuentaAhorros.getAttrs().add(new AttrModel("tasaInteres", "Double"));
        cuentaAhorros.getMethods().add("aplicarInteres()");

        model.getClasses().add(cuenta);
        model.getClasses().add(cuentaAhorros);

        // CuentaAhorros hereda de Cuenta
        RelationModel herencia = new RelationModel();
        herencia.setFromId("cls_ahorros");
        herencia.setToId("cls_cuenta");
        herencia.setFromName("CuentaAhorros");
        herencia.setToName("Cuenta");
        herencia.setRelationType("inheritance");
        model.getRelations().add(herencia);

        String xmi = xmiService.exportToXmi(model);
        assertNotNull(xmi);
        assertTrue(xmi.contains("<ownedOperation"));
        assertTrue(xmi.contains("name=\"depositar\""));
        assertTrue(xmi.contains("name=\"consultarSaldo\""));
        assertTrue(xmi.contains("name=\"aplicarInteres\""));
        assertTrue(xmi.contains("<generalization"));

        // Importar nuevamente y verificar ida y vuelta perfecta
        DiagramModel imported = xmiService.importFromXmi(xmi);
        assertNotNull(imported);
        assertEquals(2, imported.getClasses().size());

        ClassModel impCuenta = imported.getClasses().stream()
                .filter(c -> c.getName().equals("Cuenta"))
                .findFirst().orElse(null);
        assertNotNull(impCuenta);
        assertEquals(2, impCuenta.getAttrs().size());
        assertEquals(2, impCuenta.getMethods().size());
        assertTrue(impCuenta.getMethods().stream().anyMatch(m -> m.contains("depositar")));
        assertTrue(impCuenta.getMethods().stream().anyMatch(m -> m.contains("consultarSaldo")));

        ClassModel impAhorros = imported.getClasses().stream()
                .filter(c -> c.getName().equals("CuentaAhorros"))
                .findFirst().orElse(null);
        assertNotNull(impAhorros);
        assertEquals(1, impAhorros.getMethods().size());
        assertTrue(impAhorros.getMethods().get(0).contains("aplicarInteres"));

        assertEquals(1, imported.getRelations().size());
        RelationModel impRel = imported.getRelations().get(0);
        assertEquals("inheritance", impRel.getEffectiveRelationType());
        assertEquals("CuentaAhorros", impRel.getFromName());
        assertEquals("Cuenta", impRel.getToName());
    }

    @Test
    void importFromStarUmlXmiFormat() {
        XmiService xmiService = new XmiService();
        String starUmlXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmi:version="2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1">
                  <uml:Model xmi:id="STAR_MODEL_1" name="StarUML_Model">
                    <packagedElement xmi:type="uml:Class" xmi:id="STAR_C1" name="Usuario">
                      <ownedAttribute xmi:type="uml:Property" xmi:id="STAR_A1" name="email" type="String"/>
                      <ownedOperation xmi:type="uml:Operation" xmi:id="STAR_OP1" name="iniciarSesion">
                        <ownedParameter xmi:type="uml:Parameter" xmi:id="P1" name="password">
                          <type xmi:type="uml:PrimitiveType" href="http://schema.omg.org/spec/UML/2.1/uml.xml#String" name="String"/>
                        </ownedParameter>
                        <ownedParameter xmi:type="uml:Parameter" xmi:id="P_RET" name="return" direction="return">
                          <type xmi:type="uml:PrimitiveType" href="http://schema.omg.org/spec/UML/2.1/uml.xml#Boolean" name="Boolean"/>
                        </ownedParameter>
                      </ownedOperation>
                    </packagedElement>
                    <packagedElement xmi:type="uml:Class" xmi:id="STAR_C2" name="Administrador">
                      <generalization xmi:type="uml:Generalization" xmi:id="STAR_GEN1" general="STAR_C1"/>
                      <ownedOperation xmi:type="uml:Operation" xmi:id="STAR_OP2" name="bloquearUsuario"/>
                    </packagedElement>
                  </uml:Model>
                </xmi:XMI>
                """;

        DiagramModel diagram = xmiService.importFromXmi(starUmlXml);
        assertNotNull(diagram);
        assertEquals("StarUML_Model", diagram.getName());
        assertEquals(2, diagram.getClasses().size());

        ClassModel admin = diagram.getClasses().stream()
                .filter(c -> c.getName().equals("Administrador"))
                .findFirst().orElse(null);
        assertNotNull(admin);
        assertEquals(1, admin.getMethods().size());
        assertTrue(admin.getMethods().get(0).contains("bloquearUsuario"));

        ClassModel user = diagram.getClasses().stream()
                .filter(c -> c.getName().equals("Usuario"))
                .findFirst().orElse(null);
        assertNotNull(user);
        assertEquals(1, user.getAttrs().size());
        assertEquals("email", user.getAttrs().get(0).getName());
        assertEquals(1, user.getMethods().size());
        assertTrue(user.getMethods().get(0).contains("iniciarSesion"));

        assertEquals(1, diagram.getRelations().size());
        RelationModel rel = diagram.getRelations().get(0);
        assertEquals("inheritance", rel.getEffectiveRelationType());
        assertEquals("Administrador", rel.getFromName());
        assertEquals("Usuario", rel.getToName());
    }
}

