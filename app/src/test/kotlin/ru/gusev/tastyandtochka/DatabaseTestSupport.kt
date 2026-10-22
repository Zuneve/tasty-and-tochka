package ru.gusev.tastyandtochka

import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

// JDBC is used only by tests to inspect data and hold independent row locks.
fun testDataSource(postgres: PostgreSQLContainer): DataSource {
    return DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
}

fun databaseArguments(postgres: PostgreSQLContainer): Array<String> {
    return arrayOf(
        "--spring.r2dbc.url=r2dbc:${postgres.jdbcUrl.removePrefix("jdbc:")}",
        "--spring.r2dbc.username=${postgres.username}",
        "--spring.r2dbc.password=${postgres.password}",
        "--spring.liquibase.url=${postgres.jdbcUrl}",
        "--spring.liquibase.user=${postgres.username}",
        "--spring.liquibase.password=${postgres.password}",
    )
}
