/*
 * DISCLAIMER
 *
 * Copyright 2016 ArangoDB GmbH, Cologne, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright holder is ArangoDB GmbH, Cologne, Germany
 */

package com.arangodb;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.arangodb.entity.ArangoDBVersion.License;
import com.arangodb.entity.CollectionType;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.entity.GraphEntity;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.GraphCreateOptions;

/**
 * @author Mark Vollmary
 *
 */
public class ArangoGraphTest extends BaseTest {

	private static final String GRAPH_NAME = "db_collection_test";
	private static final String EDGE_COL_1 = "db_edge1_collection_test";
	private static final String EDGE_COL_2 = "db_edge2_collection_test";
	private static final String EDGE_COL_3 = "db_edge3_collection_test";
	private static final String VERTEX_COL_1 = "db_vertex1_collection_test";
	private static final String VERTEX_COL_2 = "db_vertex2_collection_test";
	private static final String VERTEX_COL_3 = "db_vertex3_collection_test";
	private static final String VERTEX_COL_4 = "db_vertex4_collection_test";

	@BeforeClass
	public static void setup() throws InterruptedException, ExecutionException {
		try {
			db.graph(GRAPH_NAME).drop().get();
		} catch (final Exception e) {
		}
		for (final String collection : new String[] { VERTEX_COL_1, VERTEX_COL_2, VERTEX_COL_2, VERTEX_COL_3,
				VERTEX_COL_4 }) {
			try {
				db.createCollection(collection, null).get();
			} catch (final Exception e) {
			}
		}
		for (final String collection : new String[] { EDGE_COL_1, EDGE_COL_2 }) {
			try {
				final CollectionCreateOptions options = new CollectionCreateOptions().type(CollectionType.EDGES);
				db.createCollection(collection, options).get();
			} catch (final Exception e) {
			}
		}
		final Collection<EdgeDefinition> edgeDefinitions = new ArrayList<>();
		edgeDefinitions.add(new EdgeDefinition().collection(EDGE_COL_1).from(VERTEX_COL_1).to(VERTEX_COL_2));
		edgeDefinitions
				.add(new EdgeDefinition().collection(EDGE_COL_2).from(VERTEX_COL_2).to(VERTEX_COL_1, VERTEX_COL_3));
		db.createGraph(GRAPH_NAME, edgeDefinitions, null).get();
	}

	@After
	public void teardown() throws InterruptedException, ExecutionException {
		for (final String collection : new String[] { EDGE_COL_1, EDGE_COL_2, VERTEX_COL_1, VERTEX_COL_2, VERTEX_COL_3,
				VERTEX_COL_4 }) {
			final ArangoCollectionAsync c = db.collection(collection);
			if (c.exists().get()) {
				c.truncate().get();
			}
		}
	}

	@Test
	public void create() throws InterruptedException, ExecutionException {
		try {
			final GraphEntity result = db.graph(GRAPH_NAME + "_1").create(null).get();
			assertThat(result, is(notNullValue()));
			assertThat(result.getName(), is(GRAPH_NAME + "_1"));
		} finally {
			db.graph(GRAPH_NAME + "_1").drop();
		}
	}

	@Test
	public void getGraphs() throws InterruptedException, ExecutionException {
		final Collection<GraphEntity> graphs = db.getGraphs().get();
		assertThat(graphs, is(notNullValue()));
		assertThat(graphs.size(), is(1));
	}

	@Test
	public void getInfo() throws InterruptedException, ExecutionException {
		final GraphEntity info = db.graph(GRAPH_NAME).getInfo().get();
		assertThat(info, is(notNullValue()));
		assertThat(info.getName(), is(GRAPH_NAME));
		assertThat(info.getEdgeDefinitions().size(), is(2));
		final Iterator<EdgeDefinition> iterator = info.getEdgeDefinitions().iterator();
		final EdgeDefinition e1 = iterator.next();
		assertThat(e1.getCollection(), is(EDGE_COL_1));
		assertThat(e1.getFrom(), hasItem(VERTEX_COL_1));
		assertThat(e1.getTo(), hasItem(VERTEX_COL_2));
		final EdgeDefinition e2 = iterator.next();
		assertThat(e2.getCollection(), is(EDGE_COL_2));
		assertThat(e2.getFrom(), hasItem(VERTEX_COL_2));
		assertThat(e2.getTo(), hasItems(VERTEX_COL_1, VERTEX_COL_3));
		assertThat(info.getOrphanCollections(), is(empty()));
	}

	@Test
	public void getVertexCollections() throws InterruptedException, ExecutionException {
		final Collection<String> vertexCollections = db.graph(GRAPH_NAME).getVertexCollections().get();
		assertThat(vertexCollections, is(notNullValue()));
		assertThat(vertexCollections.size(), is(3));
		assertThat(vertexCollections, hasItems(VERTEX_COL_1, VERTEX_COL_2, VERTEX_COL_3));
	}

	@Test
	public void addVertexCollection() throws InterruptedException, ExecutionException {
		final GraphEntity graph = db.graph(GRAPH_NAME).addVertexCollection(VERTEX_COL_4).get();
		assertThat(graph, is(notNullValue()));
		final Collection<String> vertexCollections = db.graph(GRAPH_NAME).getVertexCollections().get();
		assertThat(vertexCollections, hasItems(VERTEX_COL_1, VERTEX_COL_2, VERTEX_COL_3, VERTEX_COL_4));
		setup();
	}

	@Test
	public void getEdgeCollections() throws InterruptedException, ExecutionException {
		final Collection<String> edgeCollections = db.graph(GRAPH_NAME).getEdgeDefinitions().get();
		assertThat(edgeCollections, is(notNullValue()));
		assertThat(edgeCollections.size(), is(2));
		assertThat(edgeCollections, hasItems(EDGE_COL_1, EDGE_COL_2));
	}

	@Test
	public void addEdgeDefinition() throws InterruptedException, ExecutionException {
		final GraphEntity graph = db.graph(GRAPH_NAME)
				.addEdgeDefinition(new EdgeDefinition().collection(EDGE_COL_3).from(VERTEX_COL_1).to(VERTEX_COL_2))
				.get();
		assertThat(graph, is(notNullValue()));
		final Collection<EdgeDefinition> edgeDefinitions = graph.getEdgeDefinitions();
		assertThat(edgeDefinitions.size(), is(3));
		int count = 0;
		for (final EdgeDefinition e : edgeDefinitions) {
			if (e.getCollection().equals(EDGE_COL_3)) {
				count++;
			}
		}
		assertThat(count, is(1));
		for (final EdgeDefinition e : edgeDefinitions) {
			if (e.getCollection().equals(EDGE_COL_3)) {
				assertThat(e.getFrom(), hasItem(VERTEX_COL_1));
				assertThat(e.getTo(), hasItem(VERTEX_COL_2));
			}
		}
		setup();
	}

	@Test
	public void replaceEdgeDefinition() throws InterruptedException, ExecutionException {
		final GraphEntity graph = db.graph(GRAPH_NAME)
				.replaceEdgeDefinition(new EdgeDefinition().collection(EDGE_COL_1).from(VERTEX_COL_3).to(VERTEX_COL_4))
				.get();
		final Collection<EdgeDefinition> edgeDefinitions = graph.getEdgeDefinitions();
		assertThat(edgeDefinitions.size(), is(2));
		int count = 0;
		for (final EdgeDefinition e : edgeDefinitions) {
			if (e.getCollection().equals(EDGE_COL_1)) {
				count++;
			}
		}
		assertThat(count, is(1));
		for (final EdgeDefinition e : edgeDefinitions) {
			if (e.getCollection().equals(EDGE_COL_1)) {
				assertThat(e.getFrom(), hasItem(VERTEX_COL_3));
				assertThat(e.getTo(), hasItem(VERTEX_COL_4));
			}
		}
		setup();
	}

	@Test
	public void removeEdgeDefinition() throws InterruptedException, ExecutionException {
		final GraphEntity graph = db.graph(GRAPH_NAME).removeEdgeDefinition(EDGE_COL_1).get();
		final Collection<EdgeDefinition> edgeDefinitions = graph.getEdgeDefinitions();
		assertThat(edgeDefinitions.size(), is(1));
		assertThat(edgeDefinitions.iterator().next().getCollection(), is(EDGE_COL_2));
		setup();
	}

	@Test
	public void smartGraph() throws InterruptedException, ExecutionException {
		if (arangoDB.getVersion().get().getLicense() == License.ENTERPRISE) {
			for (final String collection : new String[] { EDGE_COL_1, EDGE_COL_2, VERTEX_COL_1, VERTEX_COL_2,
					VERTEX_COL_3, VERTEX_COL_4 }) {
				try {
					db.collection(collection).drop().get();
				} catch (final Exception e) {
				}
			}
			try {
				db.graph(GRAPH_NAME).drop().get();
			} catch (final Exception e) {
			}
			final Collection<EdgeDefinition> edgeDefinitions = new ArrayList<>();
			edgeDefinitions.add(new EdgeDefinition().collection(EDGE_COL_1).from(VERTEX_COL_1).to(VERTEX_COL_2));
			edgeDefinitions
					.add(new EdgeDefinition().collection(EDGE_COL_2).from(VERTEX_COL_2).to(VERTEX_COL_1, VERTEX_COL_3));
			final GraphEntity graph = db.createGraph(GRAPH_NAME, edgeDefinitions,
				new GraphCreateOptions().isSmart(true).smartGraphAttribute("test").numberOfShards(2)).get();
			assertThat(graph, is(notNullValue()));
			assertThat(graph.getIsSmart(), is(true));
			assertThat(graph.getSmartGraphAttribute(), is("test"));
			assertThat(graph.getNumberOfShards(), is(2));
		}
	}
}
