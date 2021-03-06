/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 *  conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package org.neo4j.ogm.autoindex;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.*;

import java.util.function.Predicate;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.ogm.domain.autoindex.CompositeIndexChild;
import org.neo4j.ogm.domain.autoindex.CompositeIndexEntity;
import org.neo4j.ogm.domain.autoindex.MultipleCompositeIndexEntity;
import org.neo4j.ogm.session.SessionFactory;

/**
 * @author Frantisek Hartman
 * @author Michael J. Simons
 */
public class CompositeIndexAutoIndexManagerTest extends BaseAutoIndexManagerTestClass {

    private static final String[] INDEXES = {
        "INDEX ON :`EntityWithCompositeIndex`(`name`,`age`)",
        "INDEX ON :`EntityWithMultipleCompositeIndexes`(`name`,`age`)",
        "INDEX ON :`EntityWithMultipleCompositeIndexes`(`name`,`email`)"
    };
    private static final String CONSTRAINT = "CONSTRAINT ON (entity:EntityWithCompositeIndex) ASSERT (entity.name, entity.age) IS NODE KEY";

    public CompositeIndexAutoIndexManagerTest() {
        super(INDEXES, CompositeIndexEntity.class, CompositeIndexChild.class, MultipleCompositeIndexEntity.class);
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        assumeTrue("This test uses composite index and node key constraint and can only be run on enterprise edition",
            isEnterpriseEdition());

        assumeTrue("This tests uses composite index and can only be run on Neo4j 3.2.0 and later",
            isVersionOrGreater("3.2.0"));
    }

    @Override
    @After
    public void tearDown() throws Exception {

        super.tearDown();
        executeDrop(CONSTRAINT);
    }

    @Test
    public void testAutoIndexManagerUpdateConstraintChangedToIndex() {

        executeCreate(CONSTRAINT);

        runAutoIndex("update");

        executeForIndexes(indexes -> {
            assertThat(indexes.stream().filter(byLabel("EntityWithCompositeIndex"))).asList()
                .hasSize(1);
        });
        executeForConstraints(constraints -> assertThat(constraints).isEmpty());
    }

    @Test
    public void testMultipleCompositeIndexAnnotations() {

        try {
            runAutoIndex("update");
            executeForIndexes(indexes ->
                assertThat(indexes.stream().filter(byLabel("EntityWithMultipleCompositeIndexes"))).asList()
                    .hasSize(2)
            );
        } finally {
            executeDrop("INDEX ON :EntityWithMultipleCompositeIndexes(name, age)");
            executeDrop("INDEX ON :EntityWithMultipleCompositeIndexes(name, email)");
        }
    }

    @Test
    public void shouldSupportScanningNonEntityPackages() {
        new SessionFactory(CompositeIndexAutoIndexManagerTest.class.getName());
    }

    private static Predicate<IndexDefinition> byLabel(String label) {
        return indexDefinition -> label.equals(indexDefinition.getLabel().name());
    }
}
