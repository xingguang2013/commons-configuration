/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;

import org.apache.commons.configuration.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.apache.commons.configuration.tree.DefaultConfigurationKey;
import org.apache.commons.configuration.tree.DefaultExpressionEngine;
import org.apache.commons.configuration.tree.ImmutableNode;
import org.apache.commons.configuration.tree.NodeStructureHelper;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for {@code BaseHierarchicalConfiguration}.
 *
 * @version $Id$
 */
public class TestHierarchicalConfiguration
{
    /** The configuration to be tested. */
    private BaseHierarchicalConfiguration config;

    @Before
    public void setUp() throws Exception
    {
        ImmutableNode root =
                new ImmutableNode.Builder(1).addChild(
                        NodeStructureHelper.ROOT_TABLES_TREE).create();
        config = new BaseHierarchicalConfiguration();
        config.setRootNode(root);
    }

    /**
     * Creates a {@code DefaultConfigurationKey} object.
     *
     * @return the new key object
     */
    private static DefaultConfigurationKey createConfigurationKey()
    {
        return new DefaultConfigurationKey(DefaultExpressionEngine.INSTANCE);
    }

    @Test
    public void testSubset()
    {
        // test the subset on the first table
        Configuration subset = config.subset("tables.table(0)");
        assertEquals(NodeStructureHelper.table(0), subset.getProperty("name"));

        Object prop = subset.getProperty("fields.field.name");
        assertNotNull(prop);
        assertTrue(prop instanceof Collection);
        assertEquals(5, ((Collection<?>) prop).size());

        for (int i = 0; i < NodeStructureHelper.fieldsLength(0); i++)
        {
            DefaultConfigurationKey key = createConfigurationKey();
            key.append("fields").append("field").appendIndex(i);
            key.append("name");
            assertEquals(NodeStructureHelper.field(0, i), subset.getProperty(key.toString()));
        }

        // test the subset on the second table
        assertTrue("subset is not empty", config.subset("tables.table(2)").isEmpty());

        // test the subset on the fields
        subset = config.subset("tables.table.fields.field");
        prop = subset.getProperty("name");
        assertTrue("prop is not a collection", prop instanceof Collection);
        assertEquals(10, ((Collection<?>) prop).size());

        assertEquals(NodeStructureHelper.field(0, 0), subset.getProperty("name(0)"));

        // test the subset on the field names
        subset = config.subset("tables.table.fields.field.name");
        assertTrue("subset is not empty", subset.isEmpty());
    }

    /**
     * Tests the subset() method when the specified node has a value. This value
     * must be available in the subset, too. Related to CONFIGURATION-295.
     */
    @Test
    public void testSubsetNodeWithValue()
    {
        config.setProperty("tables.table(0).fields", "My fields");
        Configuration subset = config.subset("tables.table(0).fields");
        assertEquals("Wrong field name", NodeStructureHelper.field(0, 0), subset
                .getString("field(0).name"));
        assertEquals("Wrong value of root", "My fields", subset.getString(""));
    }

    /**
     * Tests the subset() method when the specified key selects multiple keys.
     * The resulting root node should have a value only if exactly one of the
     * selected nodes has a value. Related to CONFIGURATION-295.
     */
    @Test
    public void testSubsetMultipleNodesWithValues()
    {
        config.setProperty("tables.table(0).fields", "My fields");
        Configuration subset = config.subset("tables.table.fields");
        assertEquals("Wrong value of root", "My fields", subset.getString(""));
        config.setProperty("tables.table(1).fields", "My other fields");
        subset = config.subset("tables.table.fields");
        assertNull("Root value is not null though there are multiple values",
                subset.getString(""));
    }

    /**
     * Tests the configurationAt() method to obtain a configuration for a sub
     * tree.
     */
    @Test
    public void testConfigurationAt()
    {
        BaseHierarchicalConfiguration subConfig =
                config.configurationAt("tables.table(1)");
        assertEquals("Wrong table name", NodeStructureHelper.table(1),
                subConfig.getString("name"));
        List<Object> lstFlds = subConfig.getList("fields.field.name");
        assertEquals("Wrong number of fields",
                NodeStructureHelper.fieldsLength(1), lstFlds.size());
        for (int i = 0; i < NodeStructureHelper.fieldsLength(1); i++)
        {
            assertEquals("Wrong field at position " + i,
                    NodeStructureHelper.field(1, i), lstFlds.get(i));
        }

        subConfig.setProperty("name", "testTable");
        assertEquals("Change not visible in parent", "testTable", config
                .getString("tables.table(1).name"));
        config.setProperty("tables.table(1).fields.field(2).name", "testField");
        assertEquals("Change not visible in sub config", "testField", subConfig
                .getString("fields.field(2).name"));
    }

    /**
     * Tests whether an immutable configuration for a sub tree can be obtained.
     */
    @Test
    public void testImmutableConfigurationAt()
    {
        ImmutableHierarchicalConfiguration subConfig =
                config.immutableConfigurationAt("tables.table(1)");
        assertEquals("Wrong table name", NodeStructureHelper.table(1),
                subConfig.getString("name"));
        List<Object> lstFlds = subConfig.getList("fields.field.name");
        assertEquals("Wrong number of fields",
                NodeStructureHelper.fieldsLength(1), lstFlds.size());
        for (int i = 0; i < NodeStructureHelper.fieldsLength(1); i++)
        {
            assertEquals("Wrong field at position " + i,
                    NodeStructureHelper.field(1, i), lstFlds.get(i));
        }
    }

    /**
     * Tests whether the support updates flag is taken into account when
     * creating an immutable sub configuration.
     */
    @Test
    public void testImmutableConfigurationAtSupportUpdates()
    {
        String newTableName = NodeStructureHelper.table(1) + "_other";
        ImmutableHierarchicalConfiguration subConfig =
                config.immutableConfigurationAt("tables.table(1)", true);
        config.addProperty("tables.table(-1).name", newTableName);
        config.clearTree("tables.table(1)");
        assertEquals("Name not updated", newTableName,
                subConfig.getString("name"));
    }

    /**
     * Tests the configurationAt() method when the passed in key does not exist.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationAtUnknownSubTree()
    {
        config.configurationAt("non.existing.key");
    }

    /**
     * Tests the configurationAt() method when the passed in key selects
     * multiple nodes. This should cause an exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationAtMultipleNodes()
    {
        config.configurationAt("tables.table.name");
    }

    /**
     * Tests whether a sub configuration obtained by configurationAt() can be
     * cleared.
     */
    @Test
    public void testConfigurationAtClear()
    {
        config.addProperty("test.sub.test", "fail");
        assertEquals("Wrong index (1)", 0, config.getMaxIndex("test"));
        SubnodeConfiguration sub = config.configurationAt("test.sub");
        assertEquals("Wrong value", "fail", sub.getString("test"));
        sub.clear();
        assertNull("Key still found", config.getString("test.sub.test"));
        sub.setProperty("test", "success");
        assertEquals("Property not set", "success",
                config.getString("test.sub.test"));
        assertEquals("Wrong index (2)", 0, config.getMaxIndex("test"));
    }

    /**
     * Tests whether a {@code SubnodeConfiguration} can be cleared and its root
     * node can be removed from its parent configuration.
     */
    @Test
    public void testConfigurationAtClearAndDetach()
    {
        config.addProperty("test.sub.test", "success");
        config.addProperty("test.other", "check");
        SubnodeConfiguration sub = config.configurationAt("test.sub");
        sub.clearAndDetachFromParent();
        assertTrue("Sub not empty", sub.isEmpty());
        assertNull("Key still found", config.getString("test.sub.test"));
        sub.setProperty("test", "failure!");
        assertNull("Node not detached", config.getString("test.sub.test"));
    }

    /**
     * Helper method for checking a list of sub configurations pointing to the
     * single fields of the table configuration.
     *
     * @param lstFlds the list with sub configurations
     */
    private void checkSubConfigurations(
            List<? extends ImmutableConfiguration> lstFlds)
    {
        assertEquals("Wrong size of fields",
                NodeStructureHelper.fieldsLength(1), lstFlds.size());
        for (int i = 0; i < NodeStructureHelper.fieldsLength(1); i++)
        {
            ImmutableConfiguration sub = lstFlds.get(i);
            assertEquals("Wrong field at position " + i,
                    NodeStructureHelper.field(1, i), sub.getString("name"));
        }
    }

    /**
     * Tests the configurationsAt() method.
     */
    @Test
    public void testConfigurationsAt()
    {
        List<SubnodeConfiguration> lstFlds =
                config.configurationsAt("tables.table(1).fields.field");
        checkSubConfigurations(lstFlds);
    }

    /**
     * Tests whether a list of immutable sub configurations can be queried.
     */
    @Test
    public void testImmutableConfigurationsAt()
    {
        List<ImmutableHierarchicalConfiguration> lstFlds =
                config.immutableConfigurationsAt("tables.table(1).fields.field");
        checkSubConfigurations(lstFlds);
    }

    /**
     * Tests the configurationsAt() method when the passed in key does not
     * select any sub nodes.
     */
    @Test
    public void testConfigurationsAtEmpty()
    {
        assertTrue("List is not empty", config.configurationsAt("unknown.key")
                .isEmpty());
    }

    @Test
    public void testClone()
    {
        Configuration copy = (Configuration) config.clone();
        assertTrue(copy instanceof BaseHierarchicalConfiguration);
        checkContent(copy);
    }

    /**
     * Tests the copy constructor.
     */
    @Test
    public void testInitCopy()
    {
        BaseHierarchicalConfiguration copy = new BaseHierarchicalConfiguration(config);
        checkContent(copy);
    }

    /**
     * Tests whether the nodes of a copied configuration are independent from
     * the source configuration.
     */
    @Test
    public void testInitCopyUpdate()
    {
        BaseHierarchicalConfiguration copy = new BaseHierarchicalConfiguration(config);
        config.setProperty("tables.table(0).name", "NewTable");
        checkContent(copy);
    }

    /**
     * Tests interpolation with a subset.
     */
    @Test
    public void testInterpolationSubset()
    {
        InterpolationTestHelper.testInterpolationSubset(config);
    }

    /**
     * Tests whether interpolation with a subset configuration works over
     * multiple layers.
     */
    @Test
    public void testInterpolationSubsetMultipleLayers()
    {
        config.clear();
        config.addProperty("var", "value");
        config.addProperty("prop2.prop[@attr]", "${var}");
        Configuration sub1 = config.subset("prop2");
        Configuration sub2 = sub1.subset("prop");
        assertEquals("Wrong value", "value", sub2.getString("[@attr]"));
    }

    /**
     * Tests obtaining a configuration with all variables substituted.
     */
    @Test
    public void testInterpolatedConfiguration()
    {
        config.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        BaseHierarchicalConfiguration c = (BaseHierarchicalConfiguration) InterpolationTestHelper
                .testInterpolatedConfiguration(config);

        // tests whether the hierarchical structure has been maintained
        config = c;
        //testGetProperty();
        //TODO check content
    }

    /**
     * Tests the copy constructor when a null reference is passed.
     */
    @Test
    public void testInitCopyNull()
    {
        BaseHierarchicalConfiguration copy =
                new BaseHierarchicalConfiguration(
                        (HierarchicalConfiguration) null);
        assertTrue("Configuration not empty", copy.isEmpty());
    }

    /**
     * Tests whether immutable configurations for the children of a given node
     * can be queried.
     */
    @Test
    public void testImmutableChildConfigurationsAt()
    {
        List<ImmutableHierarchicalConfiguration> children =
                config.immutableChildConfigurationsAt("tables.table(0)");
        assertEquals("Wrong number of elements", 2, children.size());
        ImmutableHierarchicalConfiguration c1 = children.get(0);
        assertEquals("Wrong name (1)", "name", c1.getRootElementName());
        assertEquals("Wrong table name", NodeStructureHelper.table(0), c1.getString(null));
        ImmutableHierarchicalConfiguration c2 = children.get(1);
        assertEquals("Wrong name (2)", "fields", c2.getRootElementName());
        assertEquals("Wrong field name", NodeStructureHelper.field(0, 0),
                c2.getString("field(0).name"));
    }

    /**
     * Tests whether sub configurations for the children of a given node can be
     * queried.
     */
    @Test
    public void testChildConfigurationsAt()
    {
        List<SubnodeConfiguration> children =
                config.childConfigurationsAt("tables.table(0)");
        assertEquals("Wrong number of elements", 2, children.size());
        SubnodeConfiguration sub = children.get(0);
        String newTabName = "otherTabe";
        sub.setProperty(null, newTabName);
        assertEquals("Table name not changed", newTabName,
                config.getString("tables.table(0).name"));
    }

    /**
     * Tests the result of childConfigurationsAt() if the key selects multiple
     * nodes.
     */
    @Test
    public void testChildConfigurationsAtNoUniqueKey()
    {
        assertTrue("Got children", config.childConfigurationsAt("tables.table")
                .isEmpty());
    }

    /**
     * Tests the result of childConfigurationsAt() if the key does not point to
     * an existing node.
     */
    @Test
    public void testChildConfigurationsAtNotFound()
    {
        assertTrue("Got children",
                config.childConfigurationsAt("not.existing.key").isEmpty());
    }

    /**
     * Tests the initialize() method. We can only test that a new configuration
     * listener was added.
     */
    @Test
    public void testInitialize()
    {
        Collection<ConfigurationListener> listeners =
                config.getConfigurationListeners();
        config.initialize();
        assertEquals("No new listener added", listeners.size() + 1, config
                .getConfigurationListeners().size());
    }

    /**
     * Tests that calling initialize() multiple times does not initialize
     * internal structures more than once.
     */
    @Test
    public void testInitializeTwice()
    {
        Collection<ConfigurationListener> listeners =
                config.getConfigurationListeners();
        config.initialize();
        config.initialize();
        assertEquals("Too many listener added", listeners.size() + 1, config
                .getConfigurationListeners().size());
    }

    /**
     * Checks the content of the passed in configuration object. Used by some
     * tests that copy a configuration.
     *
     * @param c the configuration to check
     */
    private void checkContent(Configuration c)
    {
        for (int i = 0; i < NodeStructureHelper.tablesLength(); i++)
        {
            assertEquals(NodeStructureHelper.table(i),
                    c.getString("tables.table(" + i + ").name"));
            for (int j = 0; j < NodeStructureHelper.fieldsLength(i); j++)
            {
                assertEquals(
                        NodeStructureHelper.field(i, j),
                        c.getString("tables.table(" + i + ").fields.field(" + j
                                + ").name"));
            }
        }
    }
}
