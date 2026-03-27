package com.chua.starter.job.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobNumberGeneratorTest {

    @Test
    void shouldGenerateReadableAndDifferentNumbers() {
        String firstJobNo = JobNumberGenerator.nextJobNo();
        String secondJobNo = JobNumberGenerator.nextJobNo();
        String jobLogNo = JobNumberGenerator.nextJobLogNo();

        assertThat(firstJobNo).startsWith("JOB");
        assertThat(secondJobNo).startsWith("JOB");
        assertThat(jobLogNo).startsWith("JOBLOG");
        assertThat(secondJobNo).isNotEqualTo(firstJobNo);
        assertThat(jobLogNo).isNotEqualTo(firstJobNo);
    }
}
