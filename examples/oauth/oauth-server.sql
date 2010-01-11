
drop table properties if exists;
drop table users if exists;
drop table avatars if exists;
drop table consumers if exists;
drop table requests if exists;
drop table tokens if exists;


create table properties (
 version int not null,      -- The database schema version
 created datetime not null, -- When the database schema was created
 updated datetime not null, -- When the database schema was last modified
 realm varchar not null     -- The HTTP Auth realm
);

insert into properties values (1, now(), now(), 'oauth-server@esxx.org');


create table users (
  id identity not null,
  email varchar not null, -- The email address used to log in
  name varchar not null,  -- The users name
  ha1 varchar not null,   -- HA1 hash of the password
  uuid uuid not null,     -- The users UUID, -- used for signup/email verification
  primary key (id),
  unique index (name)
);


create table avatars (
  id identity not null,
  avatar binary not null, -- The avatar as a 128x128 pixel PNG image
  descr varchar not null, -- A description of the avatar
  owner bigint not null,  -- What user owns this avatar
  primary key (id),
  foreign key (owner) references users (id) on delete cascade
);


create table consumers (
  id identity not null,
  name varchar not null,   -- The applications name
  key uuid not null,       -- The applications UUID
  secret varchar not null, -- The applications secret key
  owner int not null,      -- The user ID of the owner
  primary key (id),
  unique index (name),
  unique index (key),
  foreign key (owner) references users (id) on delete cascade
);


create table requests (
  token uuid not null,        -- The request token
  secret varchar not null,    -- The request token secret
  callback varchar not null,  -- The callback URL or "oob"
  created timestamp not null, -- When the token was issued
  consumer bigint not null,   -- What consumer owns this request token
  primary key (token)
);


create table tokens (
  token uuid not null,        -- The access token
  secret varchar not null,    -- The access token secret
  created timestamp not null, -- When the token was issued
  owner bigint not null,      -- What user owns this access token
  consumer bigint not null,   -- What consumer owns this access token
  primary key (token),
  foreign key (owner) references users (id) on delete cascade,
  foreign key (consumer) references consumers (id) on delete cascade
);
