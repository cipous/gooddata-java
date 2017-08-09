/*
 * Copyright (C) 2004-2017, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.warehouse;

import com.gooddata.AbstractPollHandler;
import com.gooddata.AbstractService;
import com.gooddata.FutureResult;
import com.gooddata.GoodDataException;
import com.gooddata.GoodDataRestException;
import com.gooddata.PollResult;
import com.gooddata.collections.MultiPageList;
import com.gooddata.collections.Page;
import com.gooddata.collections.PageRequest;
import com.gooddata.collections.PageableList;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

import static com.gooddata.util.Validate.notEmpty;
import static com.gooddata.util.Validate.notNull;

/**
 * Provide access to warehouse API - create, update, list and delete warehouses.
 */
public class WarehouseService extends AbstractService {

    private static final String DEFAULT_SCHEMA_NAME = "default";

    /**
     * Sets RESTful HTTP Spring template. Should be called from constructor of concrete service extending
     * this abstract one.
     *
     * @param restTemplate RESTful HTTP Spring template
     */
    public WarehouseService(RestTemplate restTemplate) {
        super(restTemplate);
    }

    /**
     * Create new warehouse.
     *
     * @param warehouse warehouse to create
     *
     * @return created warehouse
     */
    public FutureResult<Warehouse> createWarehouse(Warehouse warehouse) {
        notNull(warehouse, "warehouse");
        final WarehouseTask task;
        try {
            task = restTemplate.postForObject(Warehouses.URI, warehouse, WarehouseTask.class);
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable to create Warehouse", e);
        }

        if (task == null) {
            throw new GoodDataException("Empty response when Warehouse POSTed to API");
        }

        return new PollResult<>(this, new AbstractPollHandler<WarehouseTask,Warehouse>(task.getPollUri(), WarehouseTask.class, Warehouse.class) {

            @Override
            public boolean isFinished(ClientHttpResponse response) throws IOException {
                return HttpStatus.CREATED.equals(response.getStatusCode());
            }

            @Override
            protected void onFinish() {
                if (!getResult().isEnabled()) {
                    throw new GoodDataException("Created warehouse, uri: " + getResult().getUri() + " is not enabled!");
                }
            }

            @Override
            public void handlePollResult(WarehouseTask pollResult) {
                try {
                    final Warehouse warehouse = restTemplate.getForObject(pollResult.getWarehouseUri(), Warehouse.class);
                    setResult(warehouse);
                } catch (GoodDataException | RestClientException e) {
                    throw new GoodDataException("Warehouse creation finished, but can't get created warehouse, uri: "
                            + pollResult.getWarehouseUri(), e);
                }
            }

            @Override
            public void handlePollException(final GoodDataRestException e) {
                throw new GoodDataException("Unable to create warehouse", e);
            }
        });
    }

    /**
     * Delete Warehouse.
     * @param warehouse to delete
     */
    public void removeWarehouse(Warehouse warehouse) {
        notNull(warehouse, "warehouse");
        try {
            restTemplate.delete(warehouse.getUri());
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable to delete Warehouse, uri: " + warehouse.getUri(), e);
        }
    }

    /**
     * Get Warehouse identified by given uri.
     * @param uri warehouse uri
     * @return Warehouse
     * @throws com.gooddata.GoodDataException when Warehouse can't be accessed
     */
    public Warehouse getWarehouseByUri(String uri) {
        notEmpty(uri, "uri");
        try {
            return restTemplate.getForObject(uri, Warehouse.class);
        } catch (GoodDataRestException e) {
            if (HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
                throw new WarehouseNotFoundException(uri, e);
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            throw new GoodDataException("Unable to get Warehouse instance " + uri, e);
        }
    }

    /**
     * Get Warehouse identified by given id.
     * @param id warehouse id
     * @return Warehouse
     * @throws com.gooddata.GoodDataException when Warehouse can't be accessed
     */
    public Warehouse getWarehouseById(String id) {
        notEmpty(id, "id");
        return getWarehouseByUri(uriFromId(id));
    }

    private static String uriFromId(String id) {
        return Warehouse.TEMPLATE.expand(id).toString();
    }

    /**
     * Lists Warehouses. Returns empty list in case there are no warehouses.
     * Returns only first page if there's more instances than page limit. Use {@link PageableList#stream()} to iterate
     * over all pages, or {@link MultiPageList#collectAll()} to load the entire list.
     *
     * @return MultiPageList first page of list of warehouse instances or empty list
     */
    public PageableList<Warehouse> listWarehouses() {
        return listWarehouses(new PageRequest());
    }

    /**
     * Lists Warehouses. Returns empty list in case there are no warehouses.
     * Returns requested page (by page limit and offset). Use {@link #listWarehouses()} to get first page with default setting.
     *
     * @param startPage page to be listed
     * @return MultiPageList requested page of list of instances or empty list
     */
    public PageableList<Warehouse> listWarehouses(final Page startPage) {
        notNull(startPage, "startPage");
        return new MultiPageList<>(startPage, page -> listWarehouses(getWarehousesUri(page)));
    }

    private URI getWarehousesUri() {
        return URI.create(Warehouses.URI);
    }

    private URI getWarehousesUri(final Page page) {
        return page.getPageUri(UriComponentsBuilder.fromUri(getWarehousesUri()));
    }

    private PageableList<Warehouse> listWarehouses(final URI uri) {
        try {
            final Warehouses result = restTemplate.getForObject(uri, Warehouses.class);
            if (result == null) {
                return new PageableList<>();
            }
            return result;
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable to list Warehouses", e);
        }
    }

    /**
     * Lists warehouse users. Returns empty list in case there are no users.
     * Use {@link PageableList#stream()} to iterate over all pages,
     * or {@link MultiPageList#collectAll()} to load the entire list.
     *
     * @param warehouse warehouse
     * @return MultiPageList requested page of list of instances or empty list
     */
    public PageableList<WarehouseUser> listWarehouseUsers(final Warehouse warehouse) {
        return listWarehouseUsers(warehouse, new PageRequest());
    }

    /**
     * Lists warehouse users, starting with specified page. Returns empty list in case there are no users.
     * Use {@link PageableList#stream()} to iterate over all pages,
     * or {@link MultiPageList#collectAll()} to load the entire list.
     *
     * @param warehouse warehouse
     * @param startPage page to start with
     * @return MultiPageList requested page of list of instances starting with startPage or empty list
     */
    public PageableList<WarehouseUser> listWarehouseUsers(final Warehouse warehouse, final Page startPage) {
        notNull(warehouse, "warehouse");
        notNull(warehouse.getId(), "warehouse.id");
        notNull(startPage, "startPage");

        return new MultiPageList<>(startPage,
                page -> listWarehouseUsers(warehouse, getWarehouseUsersUri(warehouse, page)));
    }

    private URI getWarehouseUsersUri(final Warehouse warehouse) {
        return WarehouseUsers.TEMPLATE.expand(warehouse.getId());
    }

    private URI getWarehouseUsersUri(final Warehouse warehouse, final Page page) {
        return page.getPageUri(UriComponentsBuilder.fromUri(getWarehouseUsersUri(warehouse)));
    }

    private PageableList<WarehouseUser> listWarehouseUsers(final Warehouse warehouse, final URI uri) {
        try {
            final WarehouseUsers result = restTemplate.getForObject(uri, WarehouseUsers.class);
            return result == null ? new PageableList<>() : result;
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable to list users of warehouse " + warehouse.getId(), e);
        }
    }

    /**
     * Add given user to given warehouse.
     *
     * @param warehouse warehouse the user should be added to
     * @param user user to be added
     * @return added user in warehouse
     */
    public FutureResult<WarehouseUser> addUserToWarehouse(final Warehouse warehouse, final WarehouseUser user) {
        notNull(user, "user");
        notNull(warehouse, "warehouse");
        notNull(warehouse.getId(), "warehouse.id");

        final WarehouseTask task;
        try {
            task = restTemplate.postForObject(WarehouseUsers.URI, user, WarehouseTask.class, warehouse.getId());
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable add user to warehouse " + warehouse.getId(), e);
        }
        if (task == null) {
            throw new GoodDataException("Empty response when user POSTed to API");
        }

        return new PollResult<>(this,
                new AbstractPollHandler<WarehouseTask, WarehouseUser>
                        (task.getPollUri(), WarehouseTask.class, WarehouseUser.class) {

            @Override
            public boolean isFinished(ClientHttpResponse response) throws IOException {
                return HttpStatus.CREATED.equals(response.getStatusCode());
            }

            @Override
            public void handlePollResult(WarehouseTask pollResult) {
                try {
                    final WarehouseUser newUser = restTemplate.getForObject(pollResult.getWarehouseUserUri(), WarehouseUser.class);
                    setResult(newUser);
                } catch (GoodDataException | RestClientException e) {
                    throw new GoodDataException("User added to warehouse, but can't get it back, uri: "
                            + pollResult.getWarehouseUserUri(), e);
                }
            }

            @Override
            public void handlePollException(final GoodDataRestException e) {
                throw new GoodDataException("Unable to add user to warehouse", e);
            }
        });
    }

    /**
     * Remove given user from warehouse instance
     * @param user to remove from warehouse
     * @return empty future result
     * @throws WarehouseUserNotFoundException when user for removal can't be found
     * @throws GoodDataException any other reason
     */
    public FutureResult<Void> removeUserFromWarehouse(final WarehouseUser user) {
        notNull(user, "user");

        final WarehouseTask task;
        try {
            task = restTemplate.exchange(user.getUri(), HttpMethod.DELETE, null, WarehouseTask.class).getBody();
        } catch (GoodDataRestException e) {
            if (HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
                throw new WarehouseUserNotFoundException(user.getUri(), e);
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            throw new GoodDataException("Unable to remove Warehouse user from instance " + user.getUri(), e);
        }
        if (task == null) {
            throw new GoodDataException("Empty response when user removed");
        }

        return new PollResult<>(this,
                new AbstractPollHandler<WarehouseTask, Void>
                        (task.getPollUri(), WarehouseTask.class, Void.class) {

                @Override
                public boolean isFinished(ClientHttpResponse response) throws IOException {
                    return HttpStatus.CREATED.equals(response.getStatusCode());
                }

                @Override
                public void handlePollResult(WarehouseTask pollResult) {
                    setResult(null);
                }

                @Override
                public void handlePollException(final GoodDataRestException e) {
                    throw new GoodDataException("Unable to remove user from warehouse", e);
                }
        });
    }

    /**
     * Updates given Warehouse.
     *
     * @param toUpdate warehouse to be updated
     * @return updated warehouse
     * @throws com.gooddata.GoodDataException when update fails
     */
    public Warehouse updateWarehouse(Warehouse toUpdate) {
        notNull(toUpdate, "warehouse to update");
        try {
            restTemplate.put(toUpdate.getUri(), toUpdate);
        } catch (GoodDataRestException | RestClientException e) {
            throw new GoodDataException("Unable to update Warehouse, uri: " + toUpdate.getUri());
        }

        return getWarehouseByUri(toUpdate.getUri());
    }

    /**
     * list schemas for Warehouse
     *
     * @param warehouse to list schemas for
     * @return MultiPageList pageable list of warehouse schemas
     */
    public PageableList<WarehouseSchema> listWarehouseSchemas(final Warehouse warehouse) {
        return listWarehouseSchemas(warehouse, new PageRequest());
    }

    /**
     * list schemas for Warehouse
     *
     * @param warehouse to list schemas for
     * @param startPage page to be listed
     * @return MultiPageList pageable list of warehouse schemas
     */
    public PageableList<WarehouseSchema> listWarehouseSchemas(final Warehouse warehouse, final Page startPage) {
        return new MultiPageList<>(startPage,
                page -> listWarehouseSchemas(getWarehouseSchemasUri(warehouse, page))
        );
    }

    private URI getWarehouseSchemasUri(final Warehouse warehouse) {
        return WarehouseSchemas.TEMPLATE.expand(warehouse.getId());
    }

    private URI getWarehouseSchemasUri(final Warehouse warehouse, final Page page) {
        return page.getPageUri(UriComponentsBuilder.fromUri(getWarehouseSchemasUri(warehouse)));
    }

    private PageableList<WarehouseSchema> listWarehouseSchemas(final URI uri) {
        try {
            final WarehouseSchemas result = restTemplate.getForObject(uri, WarehouseSchemas.class);
            if (result == null) {
                return new PageableList<>();
            }
            return result;
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable to list Warehouse schemas", e);
        }
    }

    /**
     * get warehouse schema by name
     *
     * @param warehouse to get schema for
     * @param name of schema
     * @return warehouse schema
     */
    public WarehouseSchema getWarehouseSchemaByName(final Warehouse warehouse, final String name) {
        notNull(warehouse, "warehouse");
        notEmpty(name, "name");
        final String uri = WarehouseSchema.TEMPLATE.expand(warehouse.getId(), name).toString();
        return getWarehouseSchemaByUri(uri);
    }

    /**
     * get warehouse schema by uri
     *
     * @param uri of schema
     * @return warehouse schema
     */
    public WarehouseSchema getWarehouseSchemaByUri(final String uri) {
        notEmpty(uri, "uri");
        try {
            return restTemplate.getForObject(uri, WarehouseSchema.class);
        } catch (GoodDataRestException e) {
            if (HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
                throw new WarehouseSchemaNotFoundException(uri, e);
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            throw new GoodDataException("Unable to get Warehouse instance " + uri, e);
        }
    }

    /**
     * get default warehouse schema
     *
     * @param warehouse to get default schema for
     * @return default warehouse schema
     */
    public WarehouseSchema getDefaultWarehouseSchema(final Warehouse warehouse) {
        return getWarehouseSchemaByName(warehouse, DEFAULT_SCHEMA_NAME);
    }

    /**
     * List S3 credentials for the Warehouse. Returns empty list if no credentials are found.
     *
     * @param warehouse warehouse to get S3 credentials for
     * @return PageableList with all S3 credentials belonging to the Warehouse (not null)
     */
    public PageableList<WarehouseS3Credentials> listWarehouseS3Credentials(final Warehouse warehouse) {
        notNull(warehouse, "warehouse");

        try {
            final String uri = getWarehouseS3CredentialsListUri(warehouse).toString();
            final WarehouseS3CredentialsList result = restTemplate.getForObject(uri, WarehouseS3CredentialsList.class);
            if (result == null) {
                return new PageableList<>();
            }
            return result;
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable to list Warehouse S3 credentials for warehouse with uri: "
                    + warehouse.getUri(), e);
        }
    }

    /**
     * Get S3 credentials for the Warehouse based on {@code region} and {@code accessKey}.
     *
     * @param warehouse warehouse to get S3 credentials for
     * @return single S3 credentials record (not null)
     * @throws WarehouseS3CredentialsNotFoundException if no S3 credentials for the given parameters were found
     */
    public WarehouseS3Credentials getWarehouseS3Credentials(final Warehouse warehouse,
                                                            final String region,
                                                            final String accessKey) {
        notNull(warehouse, "warehouse");
        notEmpty(region, "region");
        notEmpty(accessKey, "accessKey");

        final String uri = getWarehouseS3CredentialsUri(warehouse, region, accessKey).toString();

        try {
            return restTemplate.getForObject(uri, WarehouseS3Credentials.class);
        } catch (GoodDataRestException e) {
            if (HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
                throw new WarehouseS3CredentialsNotFoundException(uri, e);
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            throw new GoodDataException("Unable to get Warehouse S3 credentials " + uri, e);
        }
    }

    /**
     * add new S3 credentials to the Warehouse
     *
     * @param warehouse     warehouse to add credentials to
     * @param s3Credentials the credentials to store
     * @return added credentials (not null)
     */
    public FutureResult<WarehouseS3Credentials> addS3CredentialsToWarehouse(final Warehouse warehouse,
                                                                            final WarehouseS3Credentials s3Credentials) {
        notNull(warehouse, "warehouse");
        notEmpty(warehouse.getId(), "warehouse.id");
        notNull(s3Credentials, "s3Credentials");

        final WarehouseTask task;
        try {
            task = restTemplate.postForObject(WarehouseS3CredentialsList.URI, s3Credentials, WarehouseTask.class,
                    warehouse.getId());
        } catch (GoodDataException | RestClientException e) {
            throw new GoodDataException("Unable add S3 credentials to warehouse " + warehouse.getId(), e);
        }
        if (task == null) {
            throw new GoodDataException("Empty response when S3 credentials POSTed to API");
        }

        return new PollResult<>(this,
                new AbstractPollHandler<WarehouseTask, WarehouseS3Credentials>
                        (task.getPollUri(), WarehouseTask.class, WarehouseS3Credentials.class) {

                    @Override
                    public boolean isFinished(ClientHttpResponse response) throws IOException {
                        return HttpStatus.CREATED.equals(response.getStatusCode());
                    }

                    @Override
                    public void handlePollResult(WarehouseTask pollResult) {
                        try {
                            final WarehouseS3Credentials created = restTemplate
                                    .getForObject(pollResult.getWarehouseS3CredentialsUri(),
                                            WarehouseS3Credentials.class);
                            setResult(created);
                        } catch (GoodDataException | RestClientException e) {
                            throw new GoodDataException(
                                    "S3 credentials added to warehouse, but can't get them back, uri: "
                                            + pollResult.getWarehouseUserUri(), e);
                        }
                    }

                    @Override
                    public void handlePollException(final GoodDataRestException e) {
                        throw new GoodDataException("Unable to add S3 credentials to warehouse, uri: "
                                + warehouse.getUri(), e);
                    }
                });
    }

    private URI getWarehouseS3CredentialsListUri(final Warehouse warehouse) {
        notEmpty(warehouse.getId(), "warehouse.id");

        return WarehouseS3CredentialsList.TEMPLATE.expand(warehouse.getId());
    }

    private URI getWarehouseS3CredentialsUri(final Warehouse warehouse, final String region, final String accessKey) {
        notEmpty(warehouse.getId(), "warehouse.id");

        return WarehouseS3Credentials.TEMPLATE.expand(warehouse.getId(), region, accessKey);
    }
}
