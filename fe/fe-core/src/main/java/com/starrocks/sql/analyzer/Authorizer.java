// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.sql.analyzer;

import com.google.common.base.Preconditions;
import com.starrocks.analysis.Expr;
import com.starrocks.analysis.TableName;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.Function;
import com.starrocks.catalog.InternalCatalog;
import com.starrocks.catalog.Table;
import com.starrocks.catalog.Type;
import com.starrocks.common.Config;
import com.starrocks.privilege.AccessControlProvider;
import com.starrocks.privilege.AccessDeniedException;
import com.starrocks.privilege.NativeAccessController;
import com.starrocks.privilege.ObjectType;
import com.starrocks.privilege.PEntryObject;
import com.starrocks.privilege.PrivilegeType;
import com.starrocks.privilege.ranger.starrocks.RangerStarRocksAccessController;
import com.starrocks.qe.ConnectContext;
import com.starrocks.server.CatalogMgr;
import com.starrocks.sql.ast.StatementBase;
import com.starrocks.sql.ast.UserIdentity;

import java.util.List;
import java.util.Set;

public class Authorizer {
    private static final AccessControlProvider INSTANCE;

    static {
        if (Config.access_control.equals("ranger")) {
            INSTANCE = new AccessControlProvider(new AuthorizerStmtVisitor(), new RangerStarRocksAccessController());
        } else {
            INSTANCE = new AccessControlProvider(new AuthorizerStmtVisitor(), new NativeAccessController());
        }
    }

    public static AccessControlProvider getInstance() {
        return INSTANCE;
    }

    public static void check(StatementBase statement, ConnectContext context) {
        getInstance().getPrivilegeCheckerVisitor().check(statement, context);
    }

    public static void checkSystemAction(UserIdentity currentUser, Set<Long> roleIds, PrivilegeType privilegeType)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkSystemAction(currentUser, roleIds, privilegeType);
    }

    public static void checkUserAction(UserIdentity currentUser, Set<Long> roleIds, UserIdentity impersonateUser,
                                       PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkUserAction(currentUser, roleIds, impersonateUser, privilegeType);
    }

    public static void checkCatalogAction(UserIdentity currentUser, Set<Long> roleIds, String catalogName,
                                          PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkCatalogAction(currentUser, roleIds, catalogName, privilegeType);
    }

    public static void checkAnyActionOnCatalog(UserIdentity currentUser, Set<Long> roleIds, String catalogName)
            throws AccessDeniedException {
        //Any user has an implicit usage permission on the internal catalog
        if (!CatalogMgr.isInternalCatalog(catalogName)) {
            getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                    .checkAnyActionOnCatalog(currentUser, roleIds, catalogName);
        }
    }

    public static void checkDbAction(UserIdentity currentUser, Set<Long> roleIds, String catalogName, String db,
                                     PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(catalogName)
                .checkDbAction(currentUser, roleIds, catalogName, db, privilegeType);
    }

    public static void checkAnyActionOnDb(UserIdentity currentUser, Set<Long> roleIds, String catalogName, String db)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(catalogName).checkAnyActionOnDb(currentUser, roleIds, catalogName, db);
    }

    public static void checkTableAction(UserIdentity currentUser, Set<Long> roleIds, String db, String table,
                                        PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkTableAction(currentUser, roleIds,
                        new TableName(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME, db, table), privilegeType);
    }

    public static void checkTableAction(UserIdentity currentUser, Set<Long> roleIds, String catalog, String db,
                                        String table, PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(catalog).checkTableAction(currentUser, roleIds,
                new TableName(catalog, db, table), privilegeType);
    }

    public static void checkTableAction(UserIdentity currentUser, Set<Long> roleIds, TableName tableName,
                                        PrivilegeType privilegeType) throws AccessDeniedException {
        String catalog = tableName.getCatalog();
        getInstance().getAccessControlOrDefault(catalog).checkTableAction(currentUser, roleIds, tableName, privilegeType);
    }

    public static void checkAnyActionOnTable(UserIdentity currentUser, Set<Long> roleIds, TableName tableName)
            throws AccessDeniedException {
        String catalog = tableName.getCatalog();
        getInstance().getAccessControlOrDefault(catalog).checkAnyActionOnTable(currentUser, roleIds, tableName);
    }

    public static void checkViewAction(UserIdentity currentUser, Set<Long> roleIds, TableName tableName,
                                       PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkViewAction(currentUser, roleIds, tableName, privilegeType);
    }

    public static void checkAnyActionOnView(UserIdentity currentUser, Set<Long> roleIds, TableName tableName)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkAnyActionOnView(currentUser, roleIds, tableName);
    }

    public static void checkMaterializedViewAction(UserIdentity currentUser, Set<Long> roleIds, TableName tableName,
                                                   PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkMaterializedViewAction(currentUser, roleIds, tableName, privilegeType);
    }

    public static void checkAnyActionOnMaterializedView(UserIdentity currentUser, Set<Long> roleIds,
                                                        TableName tableName) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkAnyActionOnMaterializedView(currentUser, roleIds, tableName);
    }

    public static void checkAnyActionOnTableLikeObject(UserIdentity currentUser, Set<Long> roleIds, String dbName,
                                                       Table tbl) throws AccessDeniedException {
        Table.TableType type = tbl.getType();
        switch (type) {
            case OLAP:
            case CLOUD_NATIVE:
            case MYSQL:
            case ELASTICSEARCH:
            case HIVE:
            case HIVE_VIEW:
            case ICEBERG:
            case HUDI:
            case JDBC:
            case DELTALAKE:
            case FILE:
            case SCHEMA:
            case PAIMON:
                checkAnyActionOnTable(currentUser, roleIds, new TableName(dbName, tbl.getName()));
                break;
            case MATERIALIZED_VIEW:
            case CLOUD_NATIVE_MATERIALIZED_VIEW:
                checkAnyActionOnMaterializedView(currentUser, roleIds, new TableName(dbName, tbl.getName()));
                break;
            case VIEW:
                checkAnyActionOnView(currentUser, roleIds, new TableName(dbName, tbl.getName()));
                break;
            default:
                checkAnyActionOnTable(currentUser, roleIds, new TableName(dbName, tbl.getName()));
        }
    }

    public static void checkFunctionAction(UserIdentity currentUser, Set<Long> roleIds, Database database,
                                           Function function, PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkFunctionAction(currentUser, roleIds, database, function, privilegeType);
    }

    public static void checkAnyActionOnFunction(UserIdentity currentUser, Set<Long> roleIds, String database, Function function)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkAnyActionOnFunction(currentUser, roleIds, database, function);
    }

    public static void checkGlobalFunctionAction(UserIdentity currentUser, Set<Long> roleIds, Function function,
                                                 PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkGlobalFunctionAction(currentUser, roleIds, function, privilegeType);
    }

    public static void checkAnyActionOnGlobalFunction(UserIdentity currentUser, Set<Long> roleIds, Function function)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkAnyActionOnGlobalFunction(currentUser, roleIds, function);
    }

    public static void checkActionInDb(UserIdentity currentUser, Set<Long> roleIds, String db, PrivilegeType privilegeType)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkActionInDb(currentUser, roleIds, db, privilegeType);
    }

    /**
     * Check whether current user has any privilege action on the db or objects(table/view/mv) in the db.
     * Currently, it's used by `show databases` or `use database`.
     */
    public static void checkAnyActionOnOrInDb(UserIdentity currentUser, Set<Long> roleIds, String catalogName, String db)
            throws AccessDeniedException {
        Preconditions.checkNotNull(db, "db should not null");

        try {
            getInstance().getAccessControlOrDefault(catalogName).checkAnyActionOnDb(currentUser, roleIds, catalogName, db);
        } catch (AccessDeniedException e1) {
            try {
                getInstance().getAccessControlOrDefault(catalogName)
                        .checkAnyActionOnAnyTable(currentUser, roleIds, catalogName, db);
            } catch (AccessDeniedException e2) {
                if (CatalogMgr.isInternalCatalog(catalogName)) {
                    try {
                        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                                .checkAnyActionOnAnyView(currentUser, roleIds, db);
                    } catch (AccessDeniedException e3) {
                        try {
                            getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                                    .checkAnyActionOnAnyMaterializedView(currentUser, roleIds, db);
                        } catch (AccessDeniedException e4) {
                            getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                                    .checkAnyActionOnAnyFunction(currentUser, roleIds, db);
                        }
                    }
                } else {
                    throw new AccessDeniedException();
                }
            }
        }
    }

    public static void checkResourceAction(UserIdentity currentUser, Set<Long> roleIds, String name,
                                           PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkResourceAction(currentUser, roleIds, name, privilegeType);
    }

    public static void checkAnyActionOnResource(UserIdentity currentUser, Set<Long> roleIds, String name)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkAnyActionOnResource(currentUser, roleIds, name);
    }

    public static void checkResourceGroupAction(UserIdentity currentUser, Set<Long> roleIds, String name,
                                                PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkResourceGroupAction(currentUser, roleIds, name, privilegeType);
    }

    public static void checkStorageVolumeAction(UserIdentity currentUser, Set<Long> roleIds, String storageVolume,
                                                PrivilegeType privilegeType) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkStorageVolumeAction(currentUser, roleIds, storageVolume, privilegeType);
    }

    public static void checkAnyActionOnStorageVolume(UserIdentity currentUser, Set<Long> roleIds, String storageVolume)
            throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME)
                .checkAnyActionOnStorageVolume(currentUser, roleIds, storageVolume);
    }

    public static void withGrantOption(UserIdentity currentUser, Set<Long> roleIds, ObjectType type, List<PrivilegeType> wants,
                                       List<PEntryObject> objects) throws AccessDeniedException {
        getInstance().getAccessControlOrDefault(InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME).
                withGrantOption(currentUser, roleIds, type, wants, objects);
    }

    public static Expr getColumnMaskingPolicy(ConnectContext currentUser, TableName tableName, String columnName, Type type) {
        String catalog = tableName.getCatalog() == null ? InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME : tableName.getCatalog();
        return getInstance().getAccessControlOrDefault(catalog)
                .getColumnMaskingPolicy(currentUser, tableName, columnName, type);
    }

    public static Expr getRowAccessPolicy(ConnectContext currentUser, TableName tableName) {
        String catalog = tableName.getCatalog() == null ? InternalCatalog.DEFAULT_INTERNAL_CATALOG_NAME : tableName.getCatalog();
        return getInstance().getAccessControlOrDefault(catalog).getRowAccessPolicy(currentUser, tableName);
    }
}
