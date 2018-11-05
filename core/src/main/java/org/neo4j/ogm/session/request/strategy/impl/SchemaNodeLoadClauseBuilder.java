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

package org.neo4j.ogm.session.request.strategy.impl;

import static java.util.stream.Collectors.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.neo4j.ogm.metadata.schema.Node;
import org.neo4j.ogm.metadata.schema.Relationship;
import org.neo4j.ogm.metadata.schema.Schema;
import org.neo4j.ogm.session.request.strategy.LoadClauseBuilder;

/**
 * Schema based load clause builder for nodes - starts from given node variable
 *
 * @author Frantisek Hartman
 * @author Michael J. Simons
 */
public class SchemaNodeLoadClauseBuilder extends AbstractSchemaLoadClauseBuilder implements LoadClauseBuilder {

    public SchemaNodeLoadClauseBuilder(Schema schema) {
        super(schema);
    }

    public String build(String variable, String label, int depth) {

        return createExplicitMatchAndReturnClauses(label, variable, depth);
    }

    String createExplicitMatchAndReturnClauses(String label, String variable, int maxDepth) {

        StringBuilder loadClause = new StringBuilder();
        RelationshipIterator iterator = new RelationshipIterator(schema.findNode(label), variable, maxDepth);
        while (iterator.hasNext()) {
            loadClause.append(" ").append(iterator.next());
        }

        String returnClause;
        if(iterator.getDepth() == 0) {
            returnClause = "RETURN " + variable;
        } else {
            returnClause = Stream.iterate(0, i -> i + 1).limit(iterator.getDepth())
                .map(i -> "p" + i)
                .collect(joining(",", " RETURN ", ""));
        }
        loadClause.append(returnClause);

        return loadClause.toString();
    }

    static class RelationshipIterator implements Iterator<String> {

        private final String variable;
        private final int maxDepth;

        private List<Relationship> traversableRelationships;
        private final Set<Node> visitedSourceNode = new HashSet<>();
        private int depth = 0;

        RelationshipIterator(Node startNode, String variable, int maxDepth) {

            this.traversableRelationships = extractRelationships(startNode).collect(toList());
            this.variable = variable;
            this.maxDepth = maxDepth < 0 ? Integer.MAX_VALUE : maxDepth;
        }

        private static Stream<Relationship> extractRelationships(Node node) {
            return node.relationships().values().stream();
        }

        private static String toTypePattern(Relationship relationship) {
            return ":`" + relationship.type() + "`";
        }

        /**
         * @return The currently reached depth.
         */
        public int getDepth() {
            return depth;
        }

        @Override
        public boolean hasNext() {
            return this.depth <= this.maxDepth && !traversableRelationships.isEmpty();
        }

        @Override
        public String next() {

            String s = traversableRelationships.stream().map(RelationshipIterator::toTypePattern).sorted().collect(joining("|"));
            String m = String.format("MATCH p%d=(%s%s)-[%s*0..]-(%2$s%d)", depth, variable,
                depth == 0 ? "" : Integer.toString(depth), s, depth + 1);

            // Store all newly visited nodes
            this.visitedSourceNode
                .addAll(this.traversableRelationships.stream().map(Relationship::start).collect(toList()));

            // Recompute the next, traversable relationships
            this.traversableRelationships = this.traversableRelationships.stream()
                .map(r -> r.other(r.start()))
                .filter(n -> !visitedSourceNode.contains(n))
                .flatMap(RelationshipIterator::extractRelationships)
                .collect(toList());

            depth += 1;
            return m;
        }
    }
}
