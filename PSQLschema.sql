-- public.users definition

-- Drop table

-- DROP TABLE public.users;

CREATE TABLE public.users (
	id serial4 NOT NULL,
	bio varchar NOT NULL,
	email varchar NOT NULL,
	image varchar NOT NULL,
	username varchar NOT NULL,
	"password" varchar NOT NULL,
	CONSTRAINT users_email_key UNIQUE (email),
	CONSTRAINT users_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_username ON public.users USING btree (username);

-- public.article definition

-- Drop table

-- DROP TABLE public.article;

CREATE TABLE public.article (
	id serial4 NOT NULL,
	user_id int4 NOT NULL,
	slug text NOT NULL,
	title text NOT NULL,
	description text NOT NULL,
	body text NOT NULL,
	tag_list _text NOT NULL,
	created_at timestamp NOT NULL,
	updated_at timestamp NOT NULL,
	favorites_count int4 NOT NULL,
	CONSTRAINT article_pkey PRIMARY KEY (id),
	CONSTRAINT unique_slug UNIQUE (slug)
);


-- public.article foreign keys

ALTER TABLE public.article ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES public.users(id);

-- public.article_comments definition

-- Drop table

-- DROP TABLE public.article_comments;

CREATE TABLE public.article_comments (
	id serial4 NOT NULL,
	created_at timestamp NOT NULL,
	updated_at timestamp NOT NULL,
	body text NOT NULL,
	user_id int4 NOT NULL,
	article_id int4 NOT NULL,
	CONSTRAINT article_comments_pkey PRIMARY KEY (id)
);


-- public.article_comments foreign keys

ALTER TABLE public.article_comments ADD CONSTRAINT fk_comment_to_article FOREIGN KEY (article_id) REFERENCES public.article(id);
ALTER TABLE public.article_comments ADD CONSTRAINT fk_comment_to_user FOREIGN KEY (user_id) REFERENCES public.users(id);

-- public.favorites definition

-- Drop table

-- DROP TABLE public.favorites;

CREATE TABLE public.favorites (
	id serial4 NOT NULL,
	user_id int4 NOT NULL,
	article_id int4 NOT NULL,
	CONSTRAINT favorites_pkey PRIMARY KEY (id)
);


-- public.favorites foreign keys

ALTER TABLE public.favorites ADD CONSTRAINT fk_favorites_to_article FOREIGN KEY (article_id) REFERENCES public.article(id);
ALTER TABLE public.favorites ADD CONSTRAINT fk_favorites_to_user FOREIGN KEY (user_id) REFERENCES public.users(id);

-- public.followers definition

-- Drop table

-- DROP TABLE public.followers;

CREATE TABLE public.followers (
	id serial4 NOT NULL,
	follower int4 NOT NULL,
	followed int4 NOT NULL,
	CONSTRAINT followers_pkey PRIMARY KEY (id)
);


-- public.followers foreign keys

ALTER TABLE public.followers ADD CONSTRAINT fk_followed FOREIGN KEY (followed) REFERENCES public.users(id);
ALTER TABLE public.followers ADD CONSTRAINT fk_follower FOREIGN KEY (follower) REFERENCES public.users(id);