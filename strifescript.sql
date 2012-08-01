CREATE TABLE conflicts (
	id SERIAL NOT NULL, 
	name VARCHAR(100) NOT NULL, 
	PRIMARY KEY (id)
);

CREATE TABLE users (
	id SERIAL NOT NULL, 
	username VARCHAR(50) NOT NULL, 
	password_hash VARCHAR(60) NOT NULL, 
	PRIMARY KEY (id), 
	UNIQUE (username)
);

CREATE TABLE teams (
	id SERIAL NOT NULL, 
	name VARCHAR(100) NOT NULL, 
	conflict_id INTEGER NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(conflict_id) REFERENCES conflicts (id)
);

CREATE INDEX ix_teams_conflict_id ON teams (conflict_id);

CREATE TABLE exchanges (
	id SERIAL NOT NULL, 
	ordering INTEGER NOT NULL, 
	conflict_id INTEGER NOT NULL, 
	PRIMARY KEY (id), 
	UNIQUE (ordering, conflict_id), 
	FOREIGN KEY(conflict_id) REFERENCES conflicts (id)
);

CREATE INDEX ix_exchanges_conflict_id ON exchanges (conflict_id);
CREATE INDEX ix_exchanges_ordering ON exchanges (ordering);

CREATE TABLE scripts (
	exchange_id INTEGER NOT NULL, 
	team_id INTEGER NOT NULL, 
	actions TEXT[] NOT NULL, 
	actions_revealed INTEGER NOT NULL, 
	PRIMARY KEY (exchange_id, team_id), 
	FOREIGN KEY(exchange_id) REFERENCES exchanges (id), 
	FOREIGN KEY(team_id) REFERENCES teams (id)
);

CREATE INDEX ix_scripts_exchange_id ON scripts (exchange_id);
CREATE INDEX ix_scripts_team_id ON scripts (team_id);

CREATE TABLE belligerents (
	id SERIAL NOT NULL, 
	user_id INTEGER, 
	team_id INTEGER, 
	nym VARCHAR(50) NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id), 
	FOREIGN KEY(team_id) REFERENCES teams (id)
);

CREATE INDEX ix_belligerents_team_id ON belligerents (team_id);
CREATE INDEX ix_belligerents_user_id ON belligerents (user_id);
