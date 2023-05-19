package com.chua.shell.support.shell;

import com.chua.common.support.ansi.AnsiColor;
import com.chua.common.support.ansi.AnsiOutput;
import com.chua.shell.support.arthas.NamedThreadFactory;
import com.chua.shell.support.shell.mapping.HelpCommand;
import com.chua.shell.support.shell.mapping.SystemCommand;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * shell
 *
 * @author CH
 */
@Slf4j
public class LineShell extends Shell implements Runnable {

    /**
     *
     */
    private static final int SINGLETON = 1;
    /**
     *
     */
    private static final long KEEP_ALIVE_TIME = 0L;
    private LineReader lineReader;
    private Terminal terminal;

    private ExecutorService executorService;

    public LineShell(Object... beans) {
        super(beans);
        register(new HelpCommand());
        register(new SystemCommand());
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        executorService = new ThreadPoolExecutor(
                SINGLETON,
                SINGLETON,
                KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("shell"));

        List<Completer> irs = new LinkedList<>();
        for (String s : shellCommand.keySet()) {
            irs.add(new ArgumentCompleter(new StringsCompleter(s), NullCompleter.INSTANCE));
        }
        Completer completer = new AggregateCompleter(irs);

        try {
            this.terminal = TerminalBuilder.builder().jansi(true).color(true).system(true).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.lineReader = LineReaderBuilder.builder().terminal(terminal)
                .history(new DefaultHistory())
                .highlighter(new DefaultHighlighter())
                .completer(completer).build();

        executorService.execute(this);
    }

    @Override
    public void run() {
        String prompt = AnsiOutput.toString(AnsiColor.BRIGHT_WHITE, this.prompt + " > ");
        while (status.get()) {
            String readLine = lineReader.readLine(prompt);
            String[] split = readLine.split("\\s+");
            String command = split[0];
            if ("exist".equals(command)) {
                try {
                    close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            }

            String result = handlerAnalysis(command, null);
            terminal.writer().println("" + result);
        }
    }
}
