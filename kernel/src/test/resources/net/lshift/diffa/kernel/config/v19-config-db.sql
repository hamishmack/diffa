create table category_descriptor (category_id integer generated by default as identity (start with 1), constraint_type varchar(255) not null, prefix_length integer, max_length integer, step integer, primary key (category_id));
create table config_options (opt_key varchar(255) not null, domain varchar(255) not null, opt_val varchar(255), primary key (opt_key, domain));
create table diffs (seq_id integer generated by default as identity (start with 1), entity_id varchar(255), domain varchar(255), pair varchar(255), is_match bit, detected_at timestamp not null, last_seen timestamp not null, upstream_vsn varchar(255), downstream_vsn varchar(255), ignored bit, primary key (seq_id));
create table domains (name varchar(255) not null, primary key (name));
create table endpoint (name varchar(255) not null, domain varchar(255) not null, scan_url varchar(1024), content_retrieval_url varchar(1024), version_generation_url varchar(1024), inbound_url varchar(1024), primary key (name, domain));
create table endpoint_categories (id varchar(255) not null, domain varchar(255) not null, category_descriptor_id integer not null, name varchar(255) not null, primary key (id, domain, name));
create table endpoint_views (name varchar(255) not null, endpoint varchar(255) not null, domain varchar(255) not null, primary key (name, endpoint, domain));
create table endpoint_views_categories (name varchar(255) not null, endpoint varchar(255) not null, domain varchar(255) not null, category_descriptor_id integer not null, category_name varchar(255) not null, primary key (name, endpoint, domain, category_name));
create table escalations (name varchar(255) not null, pair_key varchar(255) not null, domain varchar(255) not null, action varchar(255) not null, action_type varchar(255) not null, event varchar(255) not null, origin varchar(255), primary key (name, pair_key, domain));
create table members (domain_name varchar(255) not null, user_name varchar(255) not null, primary key (domain_name, user_name));
create table pair (pair_key varchar(255) not null, domain varchar(255) not null, upstream varchar(255) not null, downstream varchar(255) not null, version_policy_name varchar(255), matching_timeout integer, scan_cron_spec varchar(255), allow_manual_scans bit, primary key (pair_key, domain));
create table pair_reports (name varchar(255) not null, pair_key varchar(255) not null, domain varchar(255) not null, report_type varchar(255) not null, target varchar(1024) not null, primary key (name, pair_key, domain));
create table pair_views (name varchar(255) not null, pair varchar(255) not null, domain varchar(255) not null, scan_cron_spec varchar(255), primary key (name, pair, domain));
create table pending_diffs (oid integer generated by default as identity (start with 1), entity_id varchar(255), domain varchar(255), pair varchar(255), detected_at timestamp not null, last_seen timestamp not null, upstream_vsn varchar(255), downstream_vsn varchar(255), primary key (oid));
create table prefix_category_descriptor (id integer not null, primary key (id));
create table range_category_descriptor (id integer not null, data_type varchar(255), upper_bound varchar(255), lower_bound varchar(255), max_granularity varchar(255), primary key (id));
create table repair_actions (name varchar(255) not null, pair_key varchar(255) not null, domain varchar(255) not null, url varchar(255), scope varchar(255), primary key (name, pair_key, domain));
create table set_category_descriptor (id integer not null, primary key (id));
create table set_constraint_values (value_id integer not null, value_name varchar(255) not null, primary key (value_id, value_name));
create table store_checkpoints (pair varchar(255) not null, domain varchar(255) not null, latest_version bigint not null, primary key (pair, domain));
create table system_config_options (opt_key varchar(255) not null, opt_val varchar(255), primary key (opt_key));
create table users (name varchar(255) not null, email varchar(255), password_enc varchar(255), superuser bit, token varchar(255), primary key (name), unique (token));
alter table config_options add constraint FK80C74EA1C3C204DC foreign key (domain) references domains;
create index diff_last_seen on diffs (last_seen);
create index seq_id_domain_idx on diffs (seq_id, domain);
create index diff_detection on diffs (detected_at);
create index rdiff_ismatched on diffs (is_match);
create index rdiff_domain_idx on diffs (entity_id, domain, pair);
alter table endpoint add constraint FK67C71D95C3C204DC foreign key (domain) references domains;
alter table endpoint_categories add constraint FKEE1F9F06B6D4F2CB foreign key (category_descriptor_id) references category_descriptor;
alter table endpoint_categories add constraint FKEE1F9F066D6BD5C8 foreign key (id, domain) references endpoint;
alter table endpoint_views add constraint FKBE0A5744D532E642 foreign key (endpoint, domain) references endpoint;
alter table endpoint_views_categories add constraint FKF03ED1F7B6D4F2CB foreign key (category_descriptor_id) references category_descriptor;
alter table endpoint_views_categories add constraint FKF03ED1F72F30F312 foreign key (name, endpoint, domain) references endpoint_views;
alter table escalations add constraint FK2B3C687E771B6A60 foreign key (pair_key, domain) references pair;
alter table members add constraint FK388EC9191902E93E foreign key (domain_name) references domains;
alter table members add constraint FK388EC9195A11FA9E foreign key (user_name) references users;
alter table pair add constraint FK3462DAC3C204DC foreign key (domain) references domains;
alter table pair_reports add constraint FKCEC6E15A771B6A60 foreign key (pair_key, domain) references pair;
alter table pair_views add constraint FKE0BDD4C93FEF99C0 foreign key (pair, domain) references pair;
create index pdiff_domain_idx on pending_diffs (entity_id, domain, pair);
alter table prefix_category_descriptor add constraint FK46474423466530AE foreign key (id) references category_descriptor;
alter table range_category_descriptor add constraint FKDC53C74E7A220B71 foreign key (id) references category_descriptor;
alter table repair_actions add constraint FKF6BE324B771B6A60 foreign key (pair_key, domain) references pair;
alter table set_category_descriptor add constraint FKA51D45F39810CA56 foreign key (id) references category_descriptor;
alter table set_constraint_values add constraint FK96C7B32744035BE4 foreign key (value_id) references category_descriptor;
alter table store_checkpoints add constraint FK50EE698D3FEF99C0 foreign key (pair, domain) references pair;
insert into system_config_options (opt_key, opt_val) values ('correlationStore.schemaVersion', '1');
create table schema_version (version integer not null, primary key (version));
insert into schema_version(version) values(19);
create table partition_information (table_name varchar(255) not null, version integer not null, primary key (table_name, version));
insert into domains(name) values('diffa');
insert into users(name, email, password_enc, superuser) values('guest', 'guest@diffa.io', '84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec', 1);
alter table diffs  add constraint FK5AA9592F53F69C16 foreign key (pair, domain) references pair;
alter table pending_diffs  add constraint FK75E457E44AD37D84 foreign key (pair, domain) references pair;
alter table pair  add constraint FK3462DAF68A3C7 foreign key (upstream, domain) references endpoint;
alter table pair  add constraint FK3462DAF2DA557F foreign key (downstream, domain) references endpoint;
