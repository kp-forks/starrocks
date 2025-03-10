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

// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/be/src/olap/tablet_schema.h

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

#pragma once

#include <gtest/gtest_prod.h>

#include <string_view>
#include <vector>

#include "column/chunk.h"
#include "gen_cpp/olap_file.pb.h"
#include "storage/aggregate_type.h"
#include "storage/olap_define.h"
#include "storage/type_utils.h"
#include "storage/types.h"
#include "util/c_string.h"
#include "util/once.h"

namespace starrocks {

class TabletSchemaMap;
class MemTracker;
class SegmentReaderWriterTest;
class POlapTableIndexSchema;
class TColumn;

class TabletColumn {
    struct ExtraFields {
        std::string default_value;
        std::vector<TabletColumn> sub_columns;
        bool has_default_value = false;
    };

public:
    // To developers: if you changed the typedefs, don't forget to reorder class members to
    // minimize the memory space of TabletColumn, i.e, sizeof(TabletColumn)
    using ColumnName = CString;
    using ColumnUID = int32_t;
    using ColumnLength = int32_t;
    using ColumnIndexLength = uint8_t;
    using ColumnPrecision = uint8_t;
    using ColumnScale = uint8_t;

    TabletColumn();
    TabletColumn(const ColumnPB& column);
    TabletColumn(const TColumn& column);
    TabletColumn(StorageAggregateType agg, LogicalType type);
    TabletColumn(StorageAggregateType agg, LogicalType type, bool is_nullable);
    TabletColumn(StorageAggregateType agg, LogicalType type, bool is_nullable, int32_t unique_id, size_t length);

    ~TabletColumn();

    TabletColumn(const TabletColumn& rhs);
    TabletColumn(TabletColumn&& rhs) noexcept;

    TabletColumn& operator=(const TabletColumn& rhs);
    TabletColumn& operator=(TabletColumn&& rhs) noexcept;

    void swap(TabletColumn* rhs);

    void init_from_pb(const ColumnPB& column);
    void init_from_thrift(const TColumn& column);
    void to_schema_pb(ColumnPB* column) const;

    ColumnUID unique_id() const { return _unique_id; }
    void set_unique_id(ColumnUID unique_id) { _unique_id = unique_id; }

    std::string_view name() const { return {_col_name.data(), _col_name.size()}; }
    void set_name(std::string_view name) { _col_name.assign(name.data(), name.size()); }

    LogicalType type() const { return _type; }
    void set_type(LogicalType type) { _type = type; }

    bool is_key() const { return _check_flag(kIsKeyShift); }
    void set_is_key(bool value) { _set_flag(kIsKeyShift, value); }

    bool is_nullable() const { return _check_flag(kIsNullableShift); }
    void set_is_nullable(bool value) { _set_flag(kIsNullableShift, value); }

    bool is_auto_increment() const { return _check_flag(kHasAutoIncrementShift); }
    void set_is_auto_increment(bool value) { _set_flag(kHasAutoIncrementShift, value); }

    bool is_bf_column() const { return _check_flag(kIsBfColumnShift); }
    void set_is_bf_column(bool value) { _set_flag(kIsBfColumnShift, value); }

    bool has_bitmap_index() const { return _check_flag(kHasBitmapIndexShift); }
    void set_has_bitmap_index(bool value) { _set_flag(kHasBitmapIndexShift, value); }

    bool is_sort_key() const { return _check_flag(kIsSortKey); }
    void set_is_sort_key(bool value) { _set_flag(kIsSortKey, value); }

    ColumnLength length() const { return _length; }
    void set_length(ColumnLength length) { _length = length; }

    StorageAggregateType aggregation() const { return _aggregation; }
    void set_aggregation(StorageAggregateType agg) { _aggregation = agg; }

    bool has_precision() const { return _check_flag(kHasPrecisionShift); }
    ColumnPrecision precision() const { return _precision; }
    void set_precision(ColumnPrecision precision) {
        _precision = precision;
        _set_flag(kHasPrecisionShift, true);
    }

    bool has_scale() const { return _check_flag(kHasScaleShift); }
    ColumnScale scale() const { return _scale; }
    void set_scale(ColumnScale scale) {
        _scale = scale;
        _set_flag(kHasScaleShift, true);
    }

    ColumnIndexLength index_length() const { return _index_length; }
    void set_index_length(ColumnIndexLength index_length) { _index_length = index_length; }

    bool has_default_value() const { return _extra_fields && _extra_fields->has_default_value; }

    const std::string& default_value() const {
        return _extra_fields ? _extra_fields->default_value : kEmptyDefaultValue;
    }

    void set_default_value(std::string value) {
        ExtraFields* ext = _get_or_alloc_extra_fields();
        ext->has_default_value = true;
        ext->default_value = std::move(value);
    }

    void add_sub_column(const TabletColumn& sub_column);
    void add_sub_column(TabletColumn&& sub_column);
    uint32_t subcolumn_count() const { return _extra_fields ? _extra_fields->sub_columns.size() : 0; }
    const TabletColumn& subcolumn(uint32_t i) const { return _extra_fields->sub_columns[i]; }

    friend bool operator==(const TabletColumn& a, const TabletColumn& b);
    friend bool operator!=(const TabletColumn& a, const TabletColumn& b);

    size_t estimate_field_size(size_t variable_length) const;
    static uint32_t get_field_length_by_type(LogicalType type, uint32_t string_length);

    std::string debug_string() const;

    int64_t mem_usage() const {
        int64_t mem_usage = sizeof(TabletColumn) + _col_name.size() + default_value().capacity();
        for (int i = 0; i < subcolumn_count(); i++) {
            mem_usage += subcolumn(i).mem_usage();
        }
        return mem_usage;
    }

private:
    inline static const std::string kEmptyDefaultValue;
    constexpr static uint8_t kIsKeyShift = 0;
    constexpr static uint8_t kIsNullableShift = 1;
    constexpr static uint8_t kIsBfColumnShift = 2;
    constexpr static uint8_t kHasBitmapIndexShift = 3;
    constexpr static uint8_t kHasPrecisionShift = 4;
    constexpr static uint8_t kHasScaleShift = 5;
    constexpr static uint8_t kHasAutoIncrementShift = 6;
    constexpr static uint8_t kIsSortKey = 7;

    ExtraFields* _get_or_alloc_extra_fields() {
        if (_extra_fields == nullptr) {
            _extra_fields = new ExtraFields();
        }
        return _extra_fields;
    }

    void _set_flag(uint8_t pos, bool value) {
        assert(pos < sizeof(_flags) * 8);
        if (value) {
            _flags |= (1 << pos);
        } else {
            _flags &= ~(1 << pos);
        }
    }

    bool _check_flag(uint8_t pos) const {
        assert(pos < sizeof(_flags) * 8);
        return _flags & (1 << pos);
    }

    // To developers: try to order the class members in a way to minimize the required memory space.

    ColumnName _col_name;
    ColumnUID _unique_id = 0;
    ColumnLength _length = 0;
    StorageAggregateType _aggregation = STORAGE_AGGREGATE_NONE;
    LogicalType _type = TYPE_UNKNOWN;

    ColumnIndexLength _index_length = 0;
    ColumnPrecision _precision = 0;
    ColumnScale _scale = 0;

    uint8_t _flags = 0;

    ExtraFields* _extra_fields = nullptr;
};

bool operator==(const TabletColumn& a, const TabletColumn& b);
bool operator!=(const TabletColumn& a, const TabletColumn& b);

class TabletSchema {
public:
    using SchemaId = int64_t;
    using TabletSchemaCSPtr = std::shared_ptr<const TabletSchema>;

    static std::shared_ptr<TabletSchema> create(const TabletSchemaPB& schema_pb);
    static std::shared_ptr<TabletSchema> create(const TabletSchemaPB& schema_pb, TabletSchemaMap* schema_map);
    static std::shared_ptr<TabletSchema> create(const TabletSchemaCSPtr& tablet_schema,
                                                const std::vector<int32_t>& column_indexes);
    static std::shared_ptr<TabletSchema> create_with_uid(const TabletSchemaCSPtr& tablet_schema,
                                                         const std::vector<uint32_t>& unique_column_ids);
    static std::unique_ptr<TabletSchema> copy(const std::shared_ptr<const TabletSchema>& tablet_schema);

    // Must be consistent with MaterializedIndexMeta.INVALID_SCHEMA_ID defined in
    // file ./fe/fe-core/src/main/java/com/starrocks/catalog/MaterializedIndexMeta.java
    constexpr static SchemaId invalid_id() { return 0; }

    TabletSchema() = default;
    explicit TabletSchema(const TabletSchemaPB& schema_pb);
    // Does NOT take ownership of |schema_map| and |schema_map| must outlive TabletSchema.
    TabletSchema(const TabletSchemaPB& schema_pb, TabletSchemaMap* schema_map);

    ~TabletSchema();

    void to_schema_pb(TabletSchemaPB* tablet_meta_pb) const;

    // Caller should always check the returned value with `invalid_id()`.
    SchemaId id() const { return _id; }
    size_t estimate_row_size(size_t variable_len) const;
    int32_t field_index(int32_t col_unique_id) const;
    size_t field_index(std::string_view field_name) const;
    const TabletColumn& column(size_t ordinal) const;
    const std::vector<TabletColumn>& columns() const;
    const std::vector<ColumnId> sort_key_idxes() const { return _sort_key_idxes; }

    size_t num_columns() const { return _cols.size(); }
    size_t num_key_columns() const { return _num_key_columns; }
    size_t num_short_key_columns() const { return _num_short_key_columns; }

    size_t num_rows_per_row_block() const { return _num_rows_per_row_block; }
    KeysType keys_type() const { return static_cast<KeysType>(_keys_type); }
    size_t next_column_unique_id() const { return _next_column_unique_id; }
    bool has_bf_fpp() const { return _has_bf_fpp; }
    double bf_fpp() const { return _bf_fpp; }
    CompressionTypePB compression_type() const { return _compression_type; }
    void append_column(TabletColumn column);

    int32_t schema_version() const { return _schema_version; }
    void clear_columns();
    void copy_from(const std::shared_ptr<const TabletSchema>& tablet_schema);

    // Please call the following function with caution. Most of the time,
    // the following two functions should not be called explicitly.
    // When we do column partial update for primary key table which seperate primary keys
    // and sort keys, we will create a partial tablet schema for rowset writer. However,
    // the sort key columns maybe not exist in the partial tablet schema and the partial tablet
    // schema will keep a wrong sort key idxes and short key column num. So BE will crash in ASAN
    // mode. However, the sort_key_idxes and short_key_column_num in partial tablet schema is not
    // important actually, because the update segment file does not depend on it and the update
    // segment file will be rewrite to col file after apply. So these function are used to modify
    // the sort_key_idxes and short_key_column_num in partial tablet schema to avoid BE crash so far.
    void set_sort_key_idxes(std::vector<ColumnId> sort_key_idxes) {
        for (auto idx : _sort_key_idxes) {
            _cols[idx].set_is_sort_key(false);
        }
        _sort_key_idxes.clear();
        _sort_key_idxes.assign(sort_key_idxes.begin(), sort_key_idxes.end());
        for (auto idx : _sort_key_idxes) {
            _cols[idx].set_is_sort_key(true);
        }
    }
    void set_num_short_key_columns(uint16_t num_short_key_columns) { _num_short_key_columns = num_short_key_columns; }

    std::string debug_string() const;

    int64_t mem_usage() const {
        int64_t mem_usage = sizeof(TabletSchema);
        for (const auto& col : _cols) {
            mem_usage += col.mem_usage();
        }
        return mem_usage;
    }

    bool shared() const { return _schema_map != nullptr; }

    Schema* schema() const;

    void build_current_tablet_schema(int64_t index_id, int32_t version, const POlapTableIndexSchema& index,
                                     const std::shared_ptr<const TabletSchema>& ori_tablet_schema);

private:
    friend class SegmentReaderWriterTest;
    FRIEND_TEST(SegmentReaderWriterTest, estimate_segment_size);
    FRIEND_TEST(SegmentReaderWriterTest, TestStringDict);

    friend bool operator==(const TabletSchema& a, const TabletSchema& b);
    friend bool operator!=(const TabletSchema& a, const TabletSchema& b);

    void _init_from_pb(const TabletSchemaPB& schema);

    void _init_schema() const;

    SchemaId _id = invalid_id();
    TabletSchemaMap* _schema_map = nullptr;

    double _bf_fpp = 0;

    std::vector<TabletColumn> _cols;
    size_t _num_rows_per_row_block = 0;
    size_t _next_column_unique_id = 0;

    mutable uint32_t _num_columns = 0;
    mutable uint16_t _num_key_columns = 0;
    uint16_t _num_short_key_columns = 0;
    std::vector<ColumnId> _sort_key_idxes;
    std::unordered_set<ColumnId> _sort_key_idxes_set;

    uint8_t _keys_type = static_cast<uint8_t>(DUP_KEYS);
    CompressionTypePB _compression_type = CompressionTypePB::LZ4_FRAME;

    std::unordered_map<int32_t, int32_t> _field_id_to_index;

    bool _has_bf_fpp = false;

    mutable std::unique_ptr<starrocks::Schema> _schema;
    mutable std::once_flag _init_schema_once_flag;
    int32_t _schema_version = -1;
};

bool operator==(const TabletSchema& a, const TabletSchema& b);
bool operator!=(const TabletSchema& a, const TabletSchema& b);

using TabletSchemaSPtr = std::shared_ptr<TabletSchema>;
using TabletSchemaCSPtr = std::shared_ptr<const TabletSchema>;

} // namespace starrocks
