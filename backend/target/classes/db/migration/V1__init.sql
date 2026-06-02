-- V1 — initial schema. Baseline of the Hibernate-generated schema (now owned by Flyway).
-- Matches the JPA entities so Hibernate ddl-auto=validate passes.

create table profile (
    id                 uuid                        not null,
    name               varchar(255)                not null,
    handle             varchar(255)                not null,
    headline           varchar(255)                not null,
    bio                text                        not null,
    current_branch     varchar(255)                not null,
    current_status     varchar(255)                not null,
    available_for_work boolean                     not null,
    email              varchar(255)                not null,
    location           varchar(255)                not null,
    socials            text                        not null,
    fun_facts          text                        not null,
    stash              text,
    current_role_json  text,
    updated_at         timestamp(6) with time zone not null,
    constraint profile_pkey primary key (id)
);

create table project (
    id               uuid                        not null,
    slug             varchar(255)                not null,
    repo_name        varchar(255)                not null,
    description      varchar(255)                not null,
    language         varchar(255)                not null,
    language_color   varchar(255)                not null,
    stars            integer                     not null,
    forks            integer                     not null,
    commits          integer                     not null,
    last_commit      varchar(255)                not null,
    last_commit_msg  varchar(255)                not null,
    tags             text                        not null,
    live_url         varchar(255),
    repo_url         varchar(255),
    status           varchar(255)                not null,
    pinned           boolean                     not null,
    long_description text,
    sort_order       integer                     not null,
    created_at       timestamp(6) with time zone not null,
    updated_at       timestamp(6) with time zone not null,
    constraint project_pkey primary key (id),
    constraint uk_project_slug unique (slug)
);

create table commit_entry (
    id           uuid                        not null,
    hash         varchar(255)                not null,
    type         varchar(255)                not null,
    title        varchar(255)                not null,
    org          varchar(255)                not null,
    date         varchar(255)                not null,
    date_end     varchar(255),
    description  text                        not null,
    branch       varchar(255)                not null,
    branch_color varchar(255)                not null,
    color_key    varchar(255),
    tags         text,
    url          varchar(255),
    sort_order   integer                     not null,
    created_at   timestamp(6) with time zone not null,
    updated_at   timestamp(6) with time zone not null,
    constraint commit_entry_pkey primary key (id),
    constraint uk_commit_entry_hash unique (hash)
);

create table skill_branch (
    id            uuid                        not null,
    branch_name   varchar(255)                not null,
    color         varchar(255)                not null,
    branch_offset integer                     not null,
    created_at    timestamp(6) with time zone not null,
    updated_at    timestamp(6) with time zone not null,
    constraint skill_branch_pkey primary key (id),
    constraint uk_skill_branch_branch_name unique (branch_name)
);

create table skill (
    id         uuid                        not null,
    name       varchar(255)                not null,
    level      integer                     not null,
    tag        varchar(255),
    icon       varchar(255),
    branch_id  uuid                        not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint skill_pkey primary key (id),
    constraint uk_skill_branch_name unique (branch_id, name),
    constraint fk_skill_branch foreign key (branch_id) references skill_branch (id) on delete cascade
);

create table skill_diff (
    id         uuid                        not null,
    name       varchar(255)                not null,
    type       varchar(255)                not null,
    note       varchar(255),
    sort_order integer                     not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint skill_diff_pkey primary key (id),
    constraint uk_skill_diff_name unique (name)
);
