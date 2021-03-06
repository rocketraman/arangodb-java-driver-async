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

package com.arangodb.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.arangodb.ArangoCollectionAsync;
import com.arangodb.ArangoCursorAsync;
import com.arangodb.ArangoDBException;
import com.arangodb.ArangoDatabaseAsync;
import com.arangodb.ArangoGraphAsync;
import com.arangodb.ArangoRouteAsync;
import com.arangodb.ArangoSearchAsync;
import com.arangodb.ArangoViewAsync;
import com.arangodb.entity.AqlExecutionExplainEntity;
import com.arangodb.entity.AqlFunctionEntity;
import com.arangodb.entity.AqlParseEntity;
import com.arangodb.entity.ArangoDBVersion;
import com.arangodb.entity.CollectionEntity;
import com.arangodb.entity.CursorEntity;
import com.arangodb.entity.DatabaseEntity;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.entity.GraphEntity;
import com.arangodb.entity.IndexEntity;
import com.arangodb.entity.Permissions;
import com.arangodb.entity.QueryCachePropertiesEntity;
import com.arangodb.entity.QueryEntity;
import com.arangodb.entity.QueryTrackingPropertiesEntity;
import com.arangodb.entity.TraversalEntity;
import com.arangodb.entity.ViewEntity;
import com.arangodb.entity.ViewType;
import com.arangodb.internal.net.HostHandle;
import com.arangodb.internal.util.DocumentUtil;
import com.arangodb.model.AqlFunctionCreateOptions;
import com.arangodb.model.AqlFunctionDeleteOptions;
import com.arangodb.model.AqlFunctionGetOptions;
import com.arangodb.model.AqlQueryExplainOptions;
import com.arangodb.model.AqlQueryOptions;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.CollectionsReadOptions;
import com.arangodb.model.DocumentReadOptions;
import com.arangodb.model.GraphCreateOptions;
import com.arangodb.model.TransactionOptions;
import com.arangodb.model.TraversalOptions;
import com.arangodb.model.arangosearch.ArangoSearchCreateOptions;
import com.arangodb.velocypack.Type;
import com.arangodb.velocystream.Request;

/**
 * @author Mark Vollmary
 *
 */
public class ArangoDatabaseAsyncImpl extends InternalArangoDatabase<ArangoDBAsyncImpl, ArangoExecutorAsync>
		implements ArangoDatabaseAsync {

	protected ArangoDatabaseAsyncImpl(final ArangoDBAsyncImpl arangoDB, final String name) {
		super(arangoDB, name);
	}

	@Override
	public CompletableFuture<ArangoDBVersion> getVersion() {
		return executor.execute(getVersionRequest(), ArangoDBVersion.class);
	}

	@Override
	public CompletableFuture<Boolean> exists() {
		return getInfo().thenApply(info -> info != null).exceptionally(e -> e == null);
	}

	@Override
	public CompletableFuture<Collection<String>> getAccessibleDatabases() {
		return executor.execute(getAccessibleDatabasesRequest(), getDatabaseResponseDeserializer());
	}

	@Override
	public ArangoCollectionAsync collection(final String name) {
		return new ArangoCollectionAsyncImpl(this, name);
	}

	@Override
	public CompletableFuture<CollectionEntity> createCollection(final String name) {
		return executor.execute(createCollectionRequest(name, new CollectionCreateOptions()), CollectionEntity.class);
	}

	@Override
	public CompletableFuture<CollectionEntity> createCollection(
		final String name,
		final CollectionCreateOptions options) {
		return executor.execute(createCollectionRequest(name, options), CollectionEntity.class);
	}

	@Override
	public CompletableFuture<Collection<CollectionEntity>> getCollections() {
		return executor.execute(getCollectionsRequest(new CollectionsReadOptions()),
			getCollectionsResponseDeserializer());
	}

	@Override
	public CompletableFuture<Collection<CollectionEntity>> getCollections(final CollectionsReadOptions options) {
		return executor.execute(getCollectionsRequest(options), getCollectionsResponseDeserializer());
	}

	@Override
	public CompletableFuture<IndexEntity> getIndex(final String id) {
		DocumentUtil.validateIndexId(id);
		final String[] split = id.split("/");
		return collection(split[0]).getIndex(split[1]);
	}

	@Override
	public CompletableFuture<String> deleteIndex(final String id) {
		DocumentUtil.validateIndexId(id);
		final String[] split = id.split("/");
		return collection(split[0]).deleteIndex(split[1]);
	}

	@Override
	public CompletableFuture<Boolean> create() {
		return arango().createDatabase(name());
	}

	@Override
	public CompletableFuture<Boolean> drop() {
		return executor.execute(dropRequest(), createDropResponseDeserializer());
	}

	@Override
	public CompletableFuture<Void> grantAccess(final String user, final Permissions permissions) {
		return executor.execute(grantAccessRequest(user, permissions), Void.class);
	}

	@Override
	public CompletableFuture<Void> grantAccess(final String user) {
		return executor.execute(grantAccessRequest(user, Permissions.RW), Void.class);
	}

	@Override
	public CompletableFuture<Void> revokeAccess(final String user) {
		return executor.execute(grantAccessRequest(user, Permissions.NONE), Void.class);
	}

	@Override
	public CompletableFuture<Void> resetAccess(final String user) {
		return executor.execute(resetAccessRequest(user), Void.class);
	}

	@Override
	public CompletableFuture<Void> grantDefaultCollectionAccess(final String user, final Permissions permissions)
			throws ArangoDBException {
		return executor.execute(updateUserDefaultCollectionAccessRequest(user, permissions), Void.class);
	}

	@Override
	public CompletableFuture<Permissions> getPermissions(final String user) throws ArangoDBException {
		return executor.execute(getPermissionsRequest(user), getPermissionsResponseDeserialzer());
	}

	@Override
	public <T> CompletableFuture<ArangoCursorAsync<T>> query(
		final String query,
		final Map<String, Object> bindVars,
		final AqlQueryOptions options,
		final Class<T> type) throws ArangoDBException {
		final Request request = queryRequest(query, bindVars, options);
		final HostHandle hostHandle = new HostHandle();
		final CompletableFuture<CursorEntity> execution = executor.execute(request, CursorEntity.class, hostHandle);
		return execution.thenApply(result -> {
			return createCursor(result, type, options, hostHandle);
		});
	}

	@Override
	public <T> CompletableFuture<ArangoCursorAsync<T>> query(
		final String query,
		final AqlQueryOptions options,
		final Class<T> type) throws ArangoDBException {
		return query(query, null, options, type);
	}

	@Override
	public <T> CompletableFuture<ArangoCursorAsync<T>> query(
		final String query,
		final Map<String, Object> bindVars,
		final Class<T> type) throws ArangoDBException {
		return query(query, bindVars, null, type);
	}

	@Override
	public <T> CompletableFuture<ArangoCursorAsync<T>> query(final String query, final Class<T> type)
			throws ArangoDBException {
		return query(query, null, null, type);
	}

	@Override
	public <T> CompletableFuture<ArangoCursorAsync<T>> cursor(final String cursorId, final Class<T> type)
			throws ArangoDBException {
		final HostHandle hostHandle = new HostHandle();
		final CompletableFuture<CursorEntity> execution = executor.execute(queryNextRequest(cursorId, null),
			CursorEntity.class, hostHandle);
		return execution.thenApply(result -> {
			return createCursor(result, type, null, hostHandle);
		});
	}

	private <T> ArangoCursorAsync<T> createCursor(
		final CursorEntity result,
		final Class<T> type,
		final AqlQueryOptions options,
		final HostHandle hostHandle) {
		return new ArangoCursorAsyncImpl<>(this, new ArangoCursorExecute() {
			@Override
			public CursorEntity next(final String id) {
				final CompletableFuture<CursorEntity> result = executor.execute(queryNextRequest(id, options),
					CursorEntity.class, hostHandle);
				try {
					return result.get();
				} catch (InterruptedException | ExecutionException e) {
					throw new ArangoDBException(e);
				}
			}

			@Override
			public void close(final String id) {
				try {
					executor.execute(queryCloseRequest(id, options), Void.class, hostHandle).get();
				} catch (InterruptedException | ExecutionException e) {
					throw new ArangoDBException(e);
				}
			}
		}, type, result);
	}

	@Override
	public CompletableFuture<AqlExecutionExplainEntity> explainQuery(
		final String query,
		final Map<String, Object> bindVars,
		final AqlQueryExplainOptions options) {
		return executor.execute(explainQueryRequest(query, bindVars, options), AqlExecutionExplainEntity.class);
	}

	@Override
	public CompletableFuture<AqlParseEntity> parseQuery(final String query) {
		return executor.execute(parseQueryRequest(query), AqlParseEntity.class);
	}

	@Override
	public CompletableFuture<Void> clearQueryCache() {
		return executor.execute(clearQueryCacheRequest(), Void.class);
	}

	@Override
	public CompletableFuture<QueryCachePropertiesEntity> getQueryCacheProperties() {
		return executor.execute(getQueryCachePropertiesRequest(), QueryCachePropertiesEntity.class);
	}

	@Override
	public CompletableFuture<QueryCachePropertiesEntity> setQueryCacheProperties(
		final QueryCachePropertiesEntity properties) {
		return executor.execute(setQueryCachePropertiesRequest(properties), QueryCachePropertiesEntity.class);
	}

	@Override
	public CompletableFuture<QueryTrackingPropertiesEntity> getQueryTrackingProperties() {
		return executor.execute(getQueryTrackingPropertiesRequest(), QueryTrackingPropertiesEntity.class);
	}

	@Override
	public CompletableFuture<QueryTrackingPropertiesEntity> setQueryTrackingProperties(
		final QueryTrackingPropertiesEntity properties) {
		return executor.execute(setQueryTrackingPropertiesRequest(properties), QueryTrackingPropertiesEntity.class);
	}

	@Override
	public CompletableFuture<Collection<QueryEntity>> getCurrentlyRunningQueries() {
		return executor.execute(getCurrentlyRunningQueriesRequest(), new Type<Collection<QueryEntity>>() {
		}.getType());
	}

	@Override
	public CompletableFuture<Collection<QueryEntity>> getSlowQueries() {
		return executor.execute(getSlowQueriesRequest(), new Type<Collection<QueryEntity>>() {
		}.getType());
	}

	@Override
	public CompletableFuture<Void> clearSlowQueries() {
		return executor.execute(clearSlowQueriesRequest(), Void.class);
	}

	@Override
	public CompletableFuture<Void> killQuery(final String id) {
		return executor.execute(killQueryRequest(id), Void.class);
	}

	@Override
	public CompletableFuture<Void> createAqlFunction(
		final String name,
		final String code,
		final AqlFunctionCreateOptions options) {
		return executor.execute(createAqlFunctionRequest(name, code, options), Void.class);

	}

	@Override
	public CompletableFuture<Integer> deleteAqlFunction(final String name, final AqlFunctionDeleteOptions options) {
		return executor.execute(deleteAqlFunctionRequest(name, options), deleteAqlFunctionResponseDeserializer());
	}

	@Override
	public CompletableFuture<Collection<AqlFunctionEntity>> getAqlFunctions(final AqlFunctionGetOptions options) {
		return executor.execute(getAqlFunctionsRequest(options), getAqlFunctionsResponseDeserializer());
	}

	@Override
	public ArangoGraphAsync graph(final String name) {
		return new ArangoGraphAsyncImpl(this, name);
	}

	@Override
	public CompletableFuture<GraphEntity> createGraph(
		final String name,
		final Collection<EdgeDefinition> edgeDefinitions) {
		return executor.execute(createGraphRequest(name, edgeDefinitions, new GraphCreateOptions()),
			createGraphResponseDeserializer());
	}

	@Override
	public CompletableFuture<GraphEntity> createGraph(
		final String name,
		final Collection<EdgeDefinition> edgeDefinitions,
		final GraphCreateOptions options) {
		return executor.execute(createGraphRequest(name, edgeDefinitions, options), createGraphResponseDeserializer());
	}

	@Override
	public CompletableFuture<Collection<GraphEntity>> getGraphs() {
		return executor.execute(getGraphsRequest(), getGraphsResponseDeserializer());
	}

	@Override
	public <T> CompletableFuture<T> transaction(
		final String action,
		final Class<T> type,
		final TransactionOptions options) {
		return executor.execute(transactionRequest(action, options), transactionResponseDeserializer(type));
	}

	@Override
	public CompletableFuture<DatabaseEntity> getInfo() {
		return executor.execute(getInfoRequest(), getInfoResponseDeserializer());
	}

	@Override
	public <V, E> CompletableFuture<TraversalEntity<V, E>> executeTraversal(
		final Class<V> vertexClass,
		final Class<E> edgeClass,
		final TraversalOptions options) {
		final Request request = executeTraversalRequest(options);
		return executor.execute(request, executeTraversalResponseDeserializer(vertexClass, edgeClass));
	}

	@Override
	public <T> CompletableFuture<T> getDocument(final String id, final Class<T> type) throws ArangoDBException {
		DocumentUtil.validateDocumentId(id);
		final String[] split = id.split("/");
		return collection(split[0]).getDocument(split[1], type);
	}

	@Override
	public <T> CompletableFuture<T> getDocument(final String id, final Class<T> type, final DocumentReadOptions options)
			throws ArangoDBException {
		DocumentUtil.validateDocumentId(id);
		final String[] split = id.split("/");
		return collection(split[0]).getDocument(split[1], type, options);
	}

	@Override
	public CompletableFuture<Void> reloadRouting() {
		return executor.execute(reloadRoutingRequest(), Void.class);
	}

	@Override
	public ArangoRouteAsync route(final String... path) {
		return new ArangoRouteAsyncImpl(this, createPath(path), Collections.<String, String> emptyMap());
	}

	@Override
	public CompletableFuture<Collection<ViewEntity>> getViews() throws ArangoDBException {
		return executor.execute(getViewsRequest(), getViewsResponseDeserializer());
	}

	@Override
	public ArangoViewAsync view(final String name) {
		return new ArangoViewAsyncImpl(this, name);
	}

	@Override
	public ArangoSearchAsync arangoSearch(final String name) {
		return new ArangoSearchAsyncImpl(this, name);
	}

	@Override
	public CompletableFuture<ViewEntity> createView(final String name, final ViewType type) throws ArangoDBException {
		return executor.execute(createViewRequest(name, type), ViewEntity.class);
	}

	@Override
	public CompletableFuture<ViewEntity> createArangoSearch(final String name, final ArangoSearchCreateOptions options)
			throws ArangoDBException {
		return executor.execute(createArangoSearchRequest(name, options), ViewEntity.class);
	}

}
