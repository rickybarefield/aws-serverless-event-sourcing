create schema Summary;

create table Summary.PersonSummary (
    personId varchar(255) not null,
    name varchar(255),
    primary key (personId)
);
