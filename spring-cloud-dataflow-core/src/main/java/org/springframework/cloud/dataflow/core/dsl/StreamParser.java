/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.core.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for stream DSL that generates {@link StreamNode}.
 *
 * @author Andy Clement
 * @author Patrick Peralta
 * @author Ilayaperumal Gopinathan
 */
public class StreamParser extends ModuleParser {

	/**
	 * Stream name (may be {@code null}).
	 */
	private final String name;

	/**
	 * Stream DSL text.
	 */
	private final String dsl;


	/**
	 * Construct a {@code StreamParser} without supplying the stream name up front.
	 * The stream name may be embedded in the definition; for example:
	 * {@code mystream = http | file}.
	 *
	 * @param dsl the stream definition DSL text
	 */
	public StreamParser(String dsl) {
		this(null, dsl);
	}

	/**
	 * Construct a {@code StreamParser} for a stream with the provided name.
	 *
	 * @param name stream name
	 * @param dsl  stream dsl text
	 */
	public StreamParser(String name, String dsl) {
		super(new Tokens(dsl));
		this.name = name;
		this.dsl = dsl;
	}

	/**
	 * Parse a stream definition.
	 *
	 * @return the AST for the parsed stream
	 * @throws ParseException
	 */
	public StreamNode parse() {
		StreamNode ast = eatStream();

		// Check the stream name, however it was specified
		if (ast.getName() != null && !isValidName(ast.getName())) {
			throw new ParseException(ast.getName(), 0, DSLMessage.ILLEGAL_STREAM_NAME, ast.getName());
		}
		if (name != null && !isValidName(name)) {
			throw new ParseException(name, 0, DSLMessage.ILLEGAL_STREAM_NAME, name);
		}

		// Check that each module has a unique label (either explicit or implicit)
		Map<String, ModuleNode> alreadySeen = new LinkedHashMap<String, ModuleNode>();
		for (int m = 0; m < ast.getModuleNodes().size(); m++) {
			ModuleNode node = ast.getModuleNodes().get(m);
			ModuleNode previous = alreadySeen.put(node.getLabelName(), node);
			if (previous != null) {
				String duplicate = node.getLabelName();
				int previousIndex = new ArrayList<String>(alreadySeen.keySet()).indexOf(duplicate);
				throw new ParseException(dsl, node.startPos, DSLMessage.DUPLICATE_LABEL,
						duplicate, previous.getName(), previousIndex, node.getName(), m);
			}
		}

		// Check if the stream name is same as that of any of its modules' names
		// Can lead to infinite recursion during resolution, when parsing a composite module.
		if (ast.getModule(name) != null) {
			throw new ParseException(dsl, dsl.indexOf(name),
					DSLMessage.STREAM_NAME_MATCHING_MODULE_NAME,
					name);
		}
		Tokens tokens = getTokens();
		if (tokens.hasNext()) {
			tokens.raiseException(tokens.peek().startPos, DSLMessage.MORE_INPUT,
					toString(tokens.next()));
		}

		return ast;
	}

	/**
	 * If a stream name is present, return it and advance the token position -
	 * otherwise return {@code null}.
	 * <p>
	 * Expected format:
	 * {@code name =}
	 *
	 * @return stream name if present
	 */
	private String eatStreamName() {
		Tokens tokens = getTokens();
		String streamName = null;
		if (tokens.lookAhead(1, TokenKind.EQUALS)) {
			if (tokens.peek(TokenKind.IDENTIFIER)) {
				streamName = tokens.eat(TokenKind.IDENTIFIER).data;
				tokens.next(); // skip '='
			}
			else {
				tokens.raiseException(tokens.peek().startPos, DSLMessage.ILLEGAL_STREAM_NAME,
						toString(tokens.peek()));
			}
		}
		return streamName;
	}

	/**
	 * Return a {@link StreamNode} based on the tokens resulting from the parsed DSL.
	 * <p>
	 * Expected format:
	 * {@code stream: (streamName) (sourceChannel) moduleList (sinkChannel)}
	 *
	 * @return {@code StreamNode} based on parsed DSL
	 */
	private StreamNode eatStream() {
		String streamName = eatStreamName();
		SourceChannelNode sourceChannelNode = eatSourceChannel();
		// This construct: :foo > :bar is a source then a sink channel
		// with no module. Special handling for that is right here:
		boolean bridge = false;
		if (sourceChannelNode != null) { // so if we are just after a '>'
			if (looksLikeChannel() && noMorePipes()) {
				bridge = true;
			}
		}
		Tokens tokens = getTokens();
		List<ModuleNode> moduleNodes = new ArrayList<>();
		if (bridge) {
			// Create a bridge module to hang the source/sink channels off
			tokens.decrementPosition(); // Rewind so we can nicely eat the sink channel
			moduleNodes.add(new ModuleNode(null, "bridge", tokens.peek().startPos,
					tokens.peek().endPos, null));
		}
		else {
			moduleNodes.addAll(eatModuleList());
		}
		SinkChannelNode sinkChannelNode = eatSinkChannel();

		// Further data is an error
		if (tokens.hasNext()) {
			Token t = tokens.peek();
			tokens.raiseException(t.startPos, DSLMessage.UNEXPECTED_DATA_AFTER_STREAMDEF, toString(t));
		}

		return new StreamNode(tokens.getExpression(), streamName, moduleNodes,
				sourceChannelNode, sinkChannelNode);
	}

	/**
	 * Return {@code true} if no more pipes are present from the current token position.
	 *
	 * @return {@code true} if no more pipes are present from the current token position
	 */
	private boolean noMorePipes() {
		return noMorePipes(getTokens().position());
	}

	/**
	 * Return {@code true} if no more pipes are present from the given position.
	 *
	 * @param position token position from which to check for the presence of pipes
	 * @return {@code true} if no more pipes are present from the given position
	 */
	private boolean noMorePipes(int position) {
		List<Token> tokenList = getTokens().getTokenStream();
		int tokenStreamLength = tokenList.size();
		while (position < tokenStreamLength) {
			if (tokenList.get(position++).getKind() == TokenKind.PIPE) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Return {@code true} if the current token position appears to be pointing
	 * at a channel.
	 *
	 * @return {@code true} if the current token position appears to be pointing
	 * at a channel
	 */
	private boolean looksLikeChannel() {
		return looksLikeChannel(getTokens().position());
	}

	/**
	 * Return {@code true} if the indicated position appears to be pointing at a channel.
	 *
	 * @param position token position to check
	 * @return {@code true} if the indicated position appears to be pointing at a channel.
	 */
	private boolean looksLikeChannel(int position) {
		Tokens tokens = getTokens();
		List<Token> tokenList = tokens.getTokenStream();
		if (tokens.hasNext() && tokenList.get(position).getKind() == TokenKind.COLON) {
			if (tokenList.get(position - 1).isKind(TokenKind.GT)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * If the current token position contains a source channel, return a
	 * {@link SourceChannelNode} and advance the token position; otherwise
	 * return {@code null}.
	 * <p>
	 * Expected format:
	 * {@code ':' identifier >}
	 * {@code ':' identifier '.' identifier >}
	 *
	 * @return a {@code SourceChannelNode} or {@code null} if the token
	 * position is not pointing at a source channel
	 */
	private SourceChannelNode eatSourceChannel() {
		Tokens tokens = getTokens();
		boolean gtBeforePipe = false;
		// Seek for a GT(>) before a PIPE(|)
		List<Token> tokenList = tokens.getTokenStream();
		for (int i = tokens.position(); i < tokenList.size(); i++) {
			Token t = tokenList.get(i);
			if (t.getKind() == TokenKind.GT) {
				gtBeforePipe = true;
				break;
			}
			else if (t.getKind() == TokenKind.PIPE) {
				break;
			}
		}
		if (!gtBeforePipe) {
			return null;
		}

		ChannelNode channelNode = eatChannelReference();
		if (channelNode == null) {
			return null;
		}
		Token gt = tokens.eat(TokenKind.GT);
		return new SourceChannelNode(channelNode, gt.endPos);
	}

	/**
	 * If the current token position contains a sink channel, return a
	 * {@link SinkChannelNode} and advance the token position; otherwise
	 * return {@code null}.
	 * <p>
	 * Expected format:
	 * {@code '>' ':' identifier}
	 *
	 * @return a {@code SinkChannelNode} or {@code null} if the token
	 * position is not pointing at a sink channel
	 */
	private SinkChannelNode eatSinkChannel() {
		Tokens tokens = getTokens();
		SinkChannelNode sinkChannelNode = null;
		if (tokens.peek(TokenKind.GT)) {
			Token gt = tokens.eat(TokenKind.GT);
			ChannelNode channelNode = eatChannelReference();
			if (channelNode == null) {
				return null;
			}
			sinkChannelNode = new SinkChannelNode(channelNode, gt.startPos);
		}
		return sinkChannelNode;
	}

	/**
	 * Return a {@link ChannelNode} for the token at the current position.
	 * <p>
	 * A channel reference is the label component when referencing a specific
	 * module/label in a stream definition.
	 *
	 * Expected format:
	 * {@code [ ':' identifier ]* [ '.' identifier ]*}
	 * <p>
	 *
	 * @return {@code ChannelNode} representing the channel reference
	 */
	private ChannelNode eatChannelReference() {
		Tokens tokens = getTokens();
		Token firstToken = tokens.next();
		if (!firstToken.isKind(TokenKind.COLON)) {
			tokens.decrementPosition();
			return null;
		}
		Token identifierToken = tokens.next();
		String nameComponent = identifierToken.stringValue();
		List<Token> channelReferenceComponents = new ArrayList<Token>();
		while (tokens.peek(TokenKind.DOT)) {
			if (!tokens.isNextAdjacent()) {
				tokens.raiseException(tokens.peek().startPos,
						DSLMessage.NO_WHITESPACE_IN_CHANNEL_DEFINITION);
			}
			tokens.next(); // skip dot
			if (!tokens.isNextAdjacent()) {
				tokens.raiseException(tokens.peek().startPos,
						DSLMessage.NO_WHITESPACE_IN_CHANNEL_DEFINITION);
			}
			channelReferenceComponents.add(tokens.eat(TokenKind.IDENTIFIER));
		}
		int endPos = identifierToken.endPos;
		if (!channelReferenceComponents.isEmpty()) {
			endPos = channelReferenceComponents.get(channelReferenceComponents.size() - 1).endPos;
		}
		return new ChannelNode(identifierToken.startPos, endPos, nameComponent,
				tokenListToStringList(channelReferenceComponents));
	}

	/**
	 * Return a list of {@link ModuleNode} starting from the current token position.
	 * <p>
	 * Expected format:
	 * {@code moduleList: module (| module)*}
	 * A stream may end in a module (if it is a sink) or be followed by a sink channel.
	 *
	 * @return a list of {@code ModuleNode}
	 */
	private List<ModuleNode> eatModuleList() {
		Tokens tokens = getTokens();
		List<ModuleNode> moduleNodes = new ArrayList<ModuleNode>();

		moduleNodes.add(eatModule());
		while (tokens.hasNext()) {
			Token t = tokens.peek();
			if (t.kind == TokenKind.PIPE) {
				tokens.next();
				moduleNodes.add(eatModule());
			}
			else {
				// might be followed by sink channel
				break;
			}
		}
		return moduleNodes;
	}

	@Override
	public String toString() {
		Tokens tokens = getTokens();
		return String.valueOf(tokens.getTokenStream()) + "\n" +
				"tokenStreamPointer=" + tokens.position() + "\n";
	}

}
