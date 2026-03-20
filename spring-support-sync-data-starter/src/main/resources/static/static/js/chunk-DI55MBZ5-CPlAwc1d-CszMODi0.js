var Ot=(t,e,r)=>new Promise((s,o)=>{var l=u=>{try{y(r.next(u))}catch(_){o(_)}},p=u=>{try{y(r.throw(u))}catch(_){o(_)}},y=u=>u.done?s(u.value):Promise.resolve(u.value).then(l,p);y((r=r.apply(t,e)).next())});import{g as ee}from"./chunk-55IACEB6-Bm6FaF9I-CJ3V0n-a.js";import{s as se}from"./chunk-QN33PNHL-BcIfoOEz-Ckvgb3oH.js";import{_ as f,l as v,c as G,r as ie,u as re,a as ae,b as ne,g as le,s as oe,q as ce,t as he,ab as ue,k as z,z as de}from"./mermaid.core-B0WXDFZp-CLU2JDNN.js";var ft,kt=function(){var t=f(function(V,n,h,a){for(h=h||{},a=V.length;a--;h[V[a]]=n);return h},"o"),e=[1,2],r=[1,3],s=[1,4],o=[2,4],l=[1,9],p=[1,11],y=[1,16],u=[1,17],_=[1,18],m=[1,19],T=[1,33],w=[1,20],$=[1,21],A=[1,22],P=[1,23],k=[1,24],d=[1,26],D=[1,27],R=[1,28],L=[1,29],B=[1,30],I=[1,31],Y=[1,32],Q=[1,35],at=[1,36],nt=[1,37],lt=[1,38],W=[1,34],S=[1,4,5,16,17,19,21,22,24,25,26,27,28,29,33,35,37,38,41,45,48,51,52,53,54,57],ot=[1,4,5,14,15,16,17,19,21,22,24,25,26,27,28,29,33,35,37,38,39,40,41,45,48,51,52,53,54,57],xt=[4,5,16,17,19,21,22,24,25,26,27,28,29,33,35,37,38,41,45,48,51,52,53,54,57],gt={trace:f(function(){},"trace"),yy:{},symbols_:{error:2,start:3,SPACE:4,NL:5,SD:6,document:7,line:8,statement:9,classDefStatement:10,styleStatement:11,cssClassStatement:12,idStatement:13,DESCR:14,"-->":15,HIDE_EMPTY:16,scale:17,WIDTH:18,COMPOSIT_STATE:19,STRUCT_START:20,STRUCT_STOP:21,STATE_DESCR:22,AS:23,ID:24,FORK:25,JOIN:26,CHOICE:27,CONCURRENT:28,note:29,notePosition:30,NOTE_TEXT:31,direction:32,acc_title:33,acc_title_value:34,acc_descr:35,acc_descr_value:36,acc_descr_multiline_value:37,CLICK:38,STRING:39,HREF:40,classDef:41,CLASSDEF_ID:42,CLASSDEF_STYLEOPTS:43,DEFAULT:44,style:45,STYLE_IDS:46,STYLEDEF_STYLEOPTS:47,class:48,CLASSENTITY_IDS:49,STYLECLASS:50,direction_tb:51,direction_bt:52,direction_rl:53,direction_lr:54,eol:55,";":56,EDGE_STATE:57,STYLE_SEPARATOR:58,left_of:59,right_of:60,$accept:0,$end:1},terminals_:{2:"error",4:"SPACE",5:"NL",6:"SD",14:"DESCR",15:"-->",16:"HIDE_EMPTY",17:"scale",18:"WIDTH",19:"COMPOSIT_STATE",20:"STRUCT_START",21:"STRUCT_STOP",22:"STATE_DESCR",23:"AS",24:"ID",25:"FORK",26:"JOIN",27:"CHOICE",28:"CONCURRENT",29:"note",31:"NOTE_TEXT",33:"acc_title",34:"acc_title_value",35:"acc_descr",36:"acc_descr_value",37:"acc_descr_multiline_value",38:"CLICK",39:"STRING",40:"HREF",41:"classDef",42:"CLASSDEF_ID",43:"CLASSDEF_STYLEOPTS",44:"DEFAULT",45:"style",46:"STYLE_IDS",47:"STYLEDEF_STYLEOPTS",48:"class",49:"CLASSENTITY_IDS",50:"STYLECLASS",51:"direction_tb",52:"direction_bt",53:"direction_rl",54:"direction_lr",56:";",57:"EDGE_STATE",58:"STYLE_SEPARATOR",59:"left_of",60:"right_of"},productions_:[0,[3,2],[3,2],[3,2],[7,0],[7,2],[8,2],[8,1],[8,1],[9,1],[9,1],[9,1],[9,1],[9,2],[9,3],[9,4],[9,1],[9,2],[9,1],[9,4],[9,3],[9,6],[9,1],[9,1],[9,1],[9,1],[9,4],[9,4],[9,1],[9,2],[9,2],[9,1],[9,5],[9,5],[10,3],[10,3],[11,3],[12,3],[32,1],[32,1],[32,1],[32,1],[55,1],[55,1],[13,1],[13,1],[13,3],[13,3],[30,1],[30,1]],performAction:f(function(n,h,a,g,E,i,Z){var c=i.length-1;switch(E){case 3:return g.setRootDoc(i[c]),i[c];case 4:this.$=[];break;case 5:i[c]!="nl"&&(i[c-1].push(i[c]),this.$=i[c-1]);break;case 6:case 7:this.$=i[c];break;case 8:this.$="nl";break;case 12:this.$=i[c];break;case 13:const tt=i[c-1];tt.description=g.trimColon(i[c]),this.$=tt;break;case 14:this.$={stmt:"relation",state1:i[c-2],state2:i[c]};break;case 15:const _t=g.trimColon(i[c]);this.$={stmt:"relation",state1:i[c-3],state2:i[c-1],description:_t};break;case 19:this.$={stmt:"state",id:i[c-3],type:"default",description:"",doc:i[c-1]};break;case 20:var U=i[c],K=i[c-2].trim();if(i[c].match(":")){var ht=i[c].split(":");U=ht[0],K=[K,ht[1]]}this.$={stmt:"state",id:U,type:"default",description:K};break;case 21:this.$={stmt:"state",id:i[c-3],type:"default",description:i[c-5],doc:i[c-1]};break;case 22:this.$={stmt:"state",id:i[c],type:"fork"};break;case 23:this.$={stmt:"state",id:i[c],type:"join"};break;case 24:this.$={stmt:"state",id:i[c],type:"choice"};break;case 25:this.$={stmt:"state",id:g.getDividerId(),type:"divider"};break;case 26:this.$={stmt:"state",id:i[c-1].trim(),note:{position:i[c-2].trim(),text:i[c].trim()}};break;case 29:this.$=i[c].trim(),g.setAccTitle(this.$);break;case 30:case 31:this.$=i[c].trim(),g.setAccDescription(this.$);break;case 32:this.$={stmt:"click",id:i[c-3],url:i[c-2],tooltip:i[c-1]};break;case 33:this.$={stmt:"click",id:i[c-3],url:i[c-1],tooltip:""};break;case 34:case 35:this.$={stmt:"classDef",id:i[c-1].trim(),classes:i[c].trim()};break;case 36:this.$={stmt:"style",id:i[c-1].trim(),styleClass:i[c].trim()};break;case 37:this.$={stmt:"applyClass",id:i[c-1].trim(),styleClass:i[c].trim()};break;case 38:g.setDirection("TB"),this.$={stmt:"dir",value:"TB"};break;case 39:g.setDirection("BT"),this.$={stmt:"dir",value:"BT"};break;case 40:g.setDirection("RL"),this.$={stmt:"dir",value:"RL"};break;case 41:g.setDirection("LR"),this.$={stmt:"dir",value:"LR"};break;case 44:case 45:this.$={stmt:"state",id:i[c].trim(),type:"default",description:""};break;case 46:this.$={stmt:"state",id:i[c-2].trim(),classes:[i[c].trim()],type:"default",description:""};break;case 47:this.$={stmt:"state",id:i[c-2].trim(),classes:[i[c].trim()],type:"default",description:""};break}},"anonymous"),table:[{3:1,4:e,5:r,6:s},{1:[3]},{3:5,4:e,5:r,6:s},{3:6,4:e,5:r,6:s},t([1,4,5,16,17,19,22,24,25,26,27,28,29,33,35,37,38,41,45,48,51,52,53,54,57],o,{7:7}),{1:[2,1]},{1:[2,2]},{1:[2,3],4:l,5:p,8:8,9:10,10:12,11:13,12:14,13:15,16:y,17:u,19:_,22:m,24:T,25:w,26:$,27:A,28:P,29:k,32:25,33:d,35:D,37:R,38:L,41:B,45:I,48:Y,51:Q,52:at,53:nt,54:lt,57:W},t(S,[2,5]),{9:39,10:12,11:13,12:14,13:15,16:y,17:u,19:_,22:m,24:T,25:w,26:$,27:A,28:P,29:k,32:25,33:d,35:D,37:R,38:L,41:B,45:I,48:Y,51:Q,52:at,53:nt,54:lt,57:W},t(S,[2,7]),t(S,[2,8]),t(S,[2,9]),t(S,[2,10]),t(S,[2,11]),t(S,[2,12],{14:[1,40],15:[1,41]}),t(S,[2,16]),{18:[1,42]},t(S,[2,18],{20:[1,43]}),{23:[1,44]},t(S,[2,22]),t(S,[2,23]),t(S,[2,24]),t(S,[2,25]),{30:45,31:[1,46],59:[1,47],60:[1,48]},t(S,[2,28]),{34:[1,49]},{36:[1,50]},t(S,[2,31]),{13:51,24:T,57:W},{42:[1,52],44:[1,53]},{46:[1,54]},{49:[1,55]},t(ot,[2,44],{58:[1,56]}),t(ot,[2,45],{58:[1,57]}),t(S,[2,38]),t(S,[2,39]),t(S,[2,40]),t(S,[2,41]),t(S,[2,6]),t(S,[2,13]),{13:58,24:T,57:W},t(S,[2,17]),t(xt,o,{7:59}),{24:[1,60]},{24:[1,61]},{23:[1,62]},{24:[2,48]},{24:[2,49]},t(S,[2,29]),t(S,[2,30]),{39:[1,63],40:[1,64]},{43:[1,65]},{43:[1,66]},{47:[1,67]},{50:[1,68]},{24:[1,69]},{24:[1,70]},t(S,[2,14],{14:[1,71]}),{4:l,5:p,8:8,9:10,10:12,11:13,12:14,13:15,16:y,17:u,19:_,21:[1,72],22:m,24:T,25:w,26:$,27:A,28:P,29:k,32:25,33:d,35:D,37:R,38:L,41:B,45:I,48:Y,51:Q,52:at,53:nt,54:lt,57:W},t(S,[2,20],{20:[1,73]}),{31:[1,74]},{24:[1,75]},{39:[1,76]},{39:[1,77]},t(S,[2,34]),t(S,[2,35]),t(S,[2,36]),t(S,[2,37]),t(ot,[2,46]),t(ot,[2,47]),t(S,[2,15]),t(S,[2,19]),t(xt,o,{7:78}),t(S,[2,26]),t(S,[2,27]),{5:[1,79]},{5:[1,80]},{4:l,5:p,8:8,9:10,10:12,11:13,12:14,13:15,16:y,17:u,19:_,21:[1,81],22:m,24:T,25:w,26:$,27:A,28:P,29:k,32:25,33:d,35:D,37:R,38:L,41:B,45:I,48:Y,51:Q,52:at,53:nt,54:lt,57:W},t(S,[2,32]),t(S,[2,33]),t(S,[2,21])],defaultActions:{5:[2,1],6:[2,2],47:[2,48],48:[2,49]},parseError:f(function(n,h){if(h.recoverable)this.trace(n);else{var a=new Error(n);throw a.hash=h,a}},"parseError"),parse:f(function(n){var h=this,a=[0],g=[],E=[null],i=[],Z=this.table,c="",U=0,K=0,ht=2,tt=1,_t=i.slice.call(arguments,1),b=Object.create(this.lexer),j={yy:{}};for(var Tt in this.yy)Object.prototype.hasOwnProperty.call(this.yy,Tt)&&(j.yy[Tt]=this.yy[Tt]);b.setInput(n,j.yy),j.yy.lexer=b,j.yy.parser=this,typeof b.yylloc=="undefined"&&(b.yylloc={});var Et=b.yylloc;i.push(Et);var Zt=b.options&&b.options.ranges;typeof j.yy.parseError=="function"?this.parseError=j.yy.parseError:this.parseError=Object.getPrototypeOf(this).parseError;function te(O){a.length=a.length-2*O,E.length=E.length-O,i.length=i.length-O}f(te,"popStack");function Lt(){var O;return O=g.pop()||b.lex()||tt,typeof O!="number"&&(O instanceof Array&&(g=O,O=g.pop()),O=h.symbols_[O]||O),O}f(Lt,"lex");for(var x,H,N,mt,X={},ut,F,It,dt;;){if(H=a[a.length-1],this.defaultActions[H]?N=this.defaultActions[H]:((x===null||typeof x=="undefined")&&(x=Lt()),N=Z[H]&&Z[H][x]),typeof N=="undefined"||!N.length||!N[0]){var vt="";dt=[];for(ut in Z[H])this.terminals_[ut]&&ut>ht&&dt.push("'"+this.terminals_[ut]+"'");b.showPosition?vt="Parse error on line "+(U+1)+`:
`+b.showPosition()+`
Expecting `+dt.join(", ")+", got '"+(this.terminals_[x]||x)+"'":vt="Parse error on line "+(U+1)+": Unexpected "+(x==tt?"end of input":"'"+(this.terminals_[x]||x)+"'"),this.parseError(vt,{text:b.match,token:this.terminals_[x]||x,line:b.yylineno,loc:Et,expected:dt})}if(N[0]instanceof Array&&N.length>1)throw new Error("Parse Error: multiple actions possible at state: "+H+", token: "+x);switch(N[0]){case 1:a.push(x),E.push(b.yytext),i.push(b.yylloc),a.push(N[1]),x=null,K=b.yyleng,c=b.yytext,U=b.yylineno,Et=b.yylloc;break;case 2:if(F=this.productions_[N[1]][1],X.$=E[E.length-F],X._$={first_line:i[i.length-(F||1)].first_line,last_line:i[i.length-1].last_line,first_column:i[i.length-(F||1)].first_column,last_column:i[i.length-1].last_column},Zt&&(X._$.range=[i[i.length-(F||1)].range[0],i[i.length-1].range[1]]),mt=this.performAction.apply(X,[c,K,U,j.yy,N[1],E,i].concat(_t)),typeof mt!="undefined")return mt;F&&(a=a.slice(0,-1*F*2),E=E.slice(0,-1*F),i=i.slice(0,-1*F)),a.push(this.productions_[N[1]][0]),E.push(X.$),i.push(X._$),It=Z[a[a.length-2]][a[a.length-1]],a.push(It);break;case 3:return!0}}return!0},"parse")},Qt=function(){var V={EOF:1,parseError:f(function(h,a){if(this.yy.parser)this.yy.parser.parseError(h,a);else throw new Error(h)},"parseError"),setInput:f(function(n,h){return this.yy=h||this.yy||{},this._input=n,this._more=this._backtrack=this.done=!1,this.yylineno=this.yyleng=0,this.yytext=this.matched=this.match="",this.conditionStack=["INITIAL"],this.yylloc={first_line:1,first_column:0,last_line:1,last_column:0},this.options.ranges&&(this.yylloc.range=[0,0]),this.offset=0,this},"setInput"),input:f(function(){var n=this._input[0];this.yytext+=n,this.yyleng++,this.offset++,this.match+=n,this.matched+=n;var h=n.match(/(?:\r\n?|\n).*/g);return h?(this.yylineno++,this.yylloc.last_line++):this.yylloc.last_column++,this.options.ranges&&this.yylloc.range[1]++,this._input=this._input.slice(1),n},"input"),unput:f(function(n){var h=n.length,a=n.split(/(?:\r\n?|\n)/g);this._input=n+this._input,this.yytext=this.yytext.substr(0,this.yytext.length-h),this.offset-=h;var g=this.match.split(/(?:\r\n?|\n)/g);this.match=this.match.substr(0,this.match.length-1),this.matched=this.matched.substr(0,this.matched.length-1),a.length-1&&(this.yylineno-=a.length-1);var E=this.yylloc.range;return this.yylloc={first_line:this.yylloc.first_line,last_line:this.yylineno+1,first_column:this.yylloc.first_column,last_column:a?(a.length===g.length?this.yylloc.first_column:0)+g[g.length-a.length].length-a[0].length:this.yylloc.first_column-h},this.options.ranges&&(this.yylloc.range=[E[0],E[0]+this.yyleng-h]),this.yyleng=this.yytext.length,this},"unput"),more:f(function(){return this._more=!0,this},"more"),reject:f(function(){if(this.options.backtrack_lexer)this._backtrack=!0;else return this.parseError("Lexical error on line "+(this.yylineno+1)+`. You can only invoke reject() in the lexer when the lexer is of the backtracking persuasion (options.backtrack_lexer = true).
`+this.showPosition(),{text:"",token:null,line:this.yylineno});return this},"reject"),less:f(function(n){this.unput(this.match.slice(n))},"less"),pastInput:f(function(){var n=this.matched.substr(0,this.matched.length-this.match.length);return(n.length>20?"...":"")+n.substr(-20).replace(/\n/g,"")},"pastInput"),upcomingInput:f(function(){var n=this.match;return n.length<20&&(n+=this._input.substr(0,20-n.length)),(n.substr(0,20)+(n.length>20?"...":"")).replace(/\n/g,"")},"upcomingInput"),showPosition:f(function(){var n=this.pastInput(),h=new Array(n.length+1).join("-");return n+this.upcomingInput()+`
`+h+"^"},"showPosition"),test_match:f(function(n,h){var a,g,E;if(this.options.backtrack_lexer&&(E={yylineno:this.yylineno,yylloc:{first_line:this.yylloc.first_line,last_line:this.last_line,first_column:this.yylloc.first_column,last_column:this.yylloc.last_column},yytext:this.yytext,match:this.match,matches:this.matches,matched:this.matched,yyleng:this.yyleng,offset:this.offset,_more:this._more,_input:this._input,yy:this.yy,conditionStack:this.conditionStack.slice(0),done:this.done},this.options.ranges&&(E.yylloc.range=this.yylloc.range.slice(0))),g=n[0].match(/(?:\r\n?|\n).*/g),g&&(this.yylineno+=g.length),this.yylloc={first_line:this.yylloc.last_line,last_line:this.yylineno+1,first_column:this.yylloc.last_column,last_column:g?g[g.length-1].length-g[g.length-1].match(/\r?\n?/)[0].length:this.yylloc.last_column+n[0].length},this.yytext+=n[0],this.match+=n[0],this.matches=n,this.yyleng=this.yytext.length,this.options.ranges&&(this.yylloc.range=[this.offset,this.offset+=this.yyleng]),this._more=!1,this._backtrack=!1,this._input=this._input.slice(n[0].length),this.matched+=n[0],a=this.performAction.call(this,this.yy,this,h,this.conditionStack[this.conditionStack.length-1]),this.done&&this._input&&(this.done=!1),a)return a;if(this._backtrack){for(var i in E)this[i]=E[i];return!1}return!1},"test_match"),next:f(function(){if(this.done)return this.EOF;this._input||(this.done=!0);var n,h,a,g;this._more||(this.yytext="",this.match="");for(var E=this._currentRules(),i=0;i<E.length;i++)if(a=this._input.match(this.rules[E[i]]),a&&(!h||a[0].length>h[0].length)){if(h=a,g=i,this.options.backtrack_lexer){if(n=this.test_match(a,E[i]),n!==!1)return n;if(this._backtrack){h=!1;continue}else return!1}else if(!this.options.flex)break}return h?(n=this.test_match(h,E[g]),n!==!1?n:!1):this._input===""?this.EOF:this.parseError("Lexical error on line "+(this.yylineno+1)+`. Unrecognized text.
`+this.showPosition(),{text:"",token:null,line:this.yylineno})},"next"),lex:f(function(){var h=this.next();return h||this.lex()},"lex"),begin:f(function(h){this.conditionStack.push(h)},"begin"),popState:f(function(){var h=this.conditionStack.length-1;return h>0?this.conditionStack.pop():this.conditionStack[0]},"popState"),_currentRules:f(function(){return this.conditionStack.length&&this.conditionStack[this.conditionStack.length-1]?this.conditions[this.conditionStack[this.conditionStack.length-1]].rules:this.conditions.INITIAL.rules},"_currentRules"),topState:f(function(h){return h=this.conditionStack.length-1-Math.abs(h||0),h>=0?this.conditionStack[h]:"INITIAL"},"topState"),pushState:f(function(h){this.begin(h)},"pushState"),stateStackSize:f(function(){return this.conditionStack.length},"stateStackSize"),options:{"case-insensitive":!0},performAction:f(function(h,a,g,E){switch(g){case 0:return 38;case 1:return 40;case 2:return 39;case 3:return 44;case 4:return 51;case 5:return 52;case 6:return 53;case 7:return 54;case 8:break;case 9:break;case 10:return 5;case 11:break;case 12:break;case 13:break;case 14:break;case 15:return this.pushState("SCALE"),17;case 16:return 18;case 17:this.popState();break;case 18:return this.begin("acc_title"),33;case 19:return this.popState(),"acc_title_value";case 20:return this.begin("acc_descr"),35;case 21:return this.popState(),"acc_descr_value";case 22:this.begin("acc_descr_multiline");break;case 23:this.popState();break;case 24:return"acc_descr_multiline_value";case 25:return this.pushState("CLASSDEF"),41;case 26:return this.popState(),this.pushState("CLASSDEFID"),"DEFAULT_CLASSDEF_ID";case 27:return this.popState(),this.pushState("CLASSDEFID"),42;case 28:return this.popState(),43;case 29:return this.pushState("CLASS"),48;case 30:return this.popState(),this.pushState("CLASS_STYLE"),49;case 31:return this.popState(),50;case 32:return this.pushState("STYLE"),45;case 33:return this.popState(),this.pushState("STYLEDEF_STYLES"),46;case 34:return this.popState(),47;case 35:return this.pushState("SCALE"),17;case 36:return 18;case 37:this.popState();break;case 38:this.pushState("STATE");break;case 39:return this.popState(),a.yytext=a.yytext.slice(0,-8).trim(),25;case 40:return this.popState(),a.yytext=a.yytext.slice(0,-8).trim(),26;case 41:return this.popState(),a.yytext=a.yytext.slice(0,-10).trim(),27;case 42:return this.popState(),a.yytext=a.yytext.slice(0,-8).trim(),25;case 43:return this.popState(),a.yytext=a.yytext.slice(0,-8).trim(),26;case 44:return this.popState(),a.yytext=a.yytext.slice(0,-10).trim(),27;case 45:return 51;case 46:return 52;case 47:return 53;case 48:return 54;case 49:this.pushState("STATE_STRING");break;case 50:return this.pushState("STATE_ID"),"AS";case 51:return this.popState(),"ID";case 52:this.popState();break;case 53:return"STATE_DESCR";case 54:return 19;case 55:this.popState();break;case 56:return this.popState(),this.pushState("struct"),20;case 57:break;case 58:return this.popState(),21;case 59:break;case 60:return this.begin("NOTE"),29;case 61:return this.popState(),this.pushState("NOTE_ID"),59;case 62:return this.popState(),this.pushState("NOTE_ID"),60;case 63:this.popState(),this.pushState("FLOATING_NOTE");break;case 64:return this.popState(),this.pushState("FLOATING_NOTE_ID"),"AS";case 65:break;case 66:return"NOTE_TEXT";case 67:return this.popState(),"ID";case 68:return this.popState(),this.pushState("NOTE_TEXT"),24;case 69:return this.popState(),a.yytext=a.yytext.substr(2).trim(),31;case 70:return this.popState(),a.yytext=a.yytext.slice(0,-8).trim(),31;case 71:return 6;case 72:return 6;case 73:return 16;case 74:return 57;case 75:return 24;case 76:return a.yytext=a.yytext.trim(),14;case 77:return 15;case 78:return 28;case 79:return 58;case 80:return 5;case 81:return"INVALID"}},"anonymous"),rules:[/^(?:click\b)/i,/^(?:href\b)/i,/^(?:"[^"]*")/i,/^(?:default\b)/i,/^(?:.*direction\s+TB[^\n]*)/i,/^(?:.*direction\s+BT[^\n]*)/i,/^(?:.*direction\s+RL[^\n]*)/i,/^(?:.*direction\s+LR[^\n]*)/i,/^(?:%%(?!\{)[^\n]*)/i,/^(?:[^\}]%%[^\n]*)/i,/^(?:[\n]+)/i,/^(?:[\s]+)/i,/^(?:((?!\n)\s)+)/i,/^(?:#[^\n]*)/i,/^(?:%[^\n]*)/i,/^(?:scale\s+)/i,/^(?:\d+)/i,/^(?:\s+width\b)/i,/^(?:accTitle\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*\{\s*)/i,/^(?:[\}])/i,/^(?:[^\}]*)/i,/^(?:classDef\s+)/i,/^(?:DEFAULT\s+)/i,/^(?:\w+\s+)/i,/^(?:[^\n]*)/i,/^(?:class\s+)/i,/^(?:(\w+)+((,\s*\w+)*))/i,/^(?:[^\n]*)/i,/^(?:style\s+)/i,/^(?:[\w,]+\s+)/i,/^(?:[^\n]*)/i,/^(?:scale\s+)/i,/^(?:\d+)/i,/^(?:\s+width\b)/i,/^(?:state\s+)/i,/^(?:.*<<fork>>)/i,/^(?:.*<<join>>)/i,/^(?:.*<<choice>>)/i,/^(?:.*\[\[fork\]\])/i,/^(?:.*\[\[join\]\])/i,/^(?:.*\[\[choice\]\])/i,/^(?:.*direction\s+TB[^\n]*)/i,/^(?:.*direction\s+BT[^\n]*)/i,/^(?:.*direction\s+RL[^\n]*)/i,/^(?:.*direction\s+LR[^\n]*)/i,/^(?:["])/i,/^(?:\s*as\s+)/i,/^(?:[^\n\{]*)/i,/^(?:["])/i,/^(?:[^"]*)/i,/^(?:[^\n\s\{]+)/i,/^(?:\n)/i,/^(?:\{)/i,/^(?:%%(?!\{)[^\n]*)/i,/^(?:\})/i,/^(?:[\n])/i,/^(?:note\s+)/i,/^(?:left of\b)/i,/^(?:right of\b)/i,/^(?:")/i,/^(?:\s*as\s*)/i,/^(?:["])/i,/^(?:[^"]*)/i,/^(?:[^\n]*)/i,/^(?:\s*[^:\n\s\-]+)/i,/^(?:\s*:[^:\n;]+)/i,/^(?:[\s\S]*?end note\b)/i,/^(?:stateDiagram\s+)/i,/^(?:stateDiagram-v2\s+)/i,/^(?:hide empty description\b)/i,/^(?:\[\*\])/i,/^(?:[^:\n\s\-\{]+)/i,/^(?:\s*:[^:\n;]+)/i,/^(?:-->)/i,/^(?:--)/i,/^(?::::)/i,/^(?:$)/i,/^(?:.)/i],conditions:{LINE:{rules:[12,13],inclusive:!1},struct:{rules:[12,13,25,29,32,38,45,46,47,48,57,58,59,60,74,75,76,77,78],inclusive:!1},FLOATING_NOTE_ID:{rules:[67],inclusive:!1},FLOATING_NOTE:{rules:[64,65,66],inclusive:!1},NOTE_TEXT:{rules:[69,70],inclusive:!1},NOTE_ID:{rules:[68],inclusive:!1},NOTE:{rules:[61,62,63],inclusive:!1},STYLEDEF_STYLEOPTS:{rules:[],inclusive:!1},STYLEDEF_STYLES:{rules:[34],inclusive:!1},STYLE_IDS:{rules:[],inclusive:!1},STYLE:{rules:[33],inclusive:!1},CLASS_STYLE:{rules:[31],inclusive:!1},CLASS:{rules:[30],inclusive:!1},CLASSDEFID:{rules:[28],inclusive:!1},CLASSDEF:{rules:[26,27],inclusive:!1},acc_descr_multiline:{rules:[23,24],inclusive:!1},acc_descr:{rules:[21],inclusive:!1},acc_title:{rules:[19],inclusive:!1},SCALE:{rules:[16,17,36,37],inclusive:!1},ALIAS:{rules:[],inclusive:!1},STATE_ID:{rules:[51],inclusive:!1},STATE_STRING:{rules:[52,53],inclusive:!1},FORK_STATE:{rules:[],inclusive:!1},STATE:{rules:[12,13,39,40,41,42,43,44,49,50,54,55,56],inclusive:!1},ID:{rules:[12,13],inclusive:!1},INITIAL:{rules:[0,1,2,3,4,5,6,7,8,9,10,11,13,14,15,18,20,22,25,29,32,35,38,56,60,71,72,73,74,75,76,77,79,80,81],inclusive:!0}}};return V}();gt.lexer=Qt;function ct(){this.yy={}}return f(ct,"Parser"),ct.prototype=gt,gt.Parser=ct,new ct}();kt.parser=kt;var Me=kt,fe="TB",Bt="TB",Rt="dir",q="state",J="root",Ct="relation",pe="classDef",Se="style",ye="applyClass",it="default",Ft="divider",Vt="fill:none",Mt="fill: #333",Ut="c",jt="text",Ht="normal",bt="rect",Dt="rectWithTitle",ge="stateStart",_e="stateEnd",Nt="divider",wt="roundedWithTitle",Te="note",Ee="noteGroup",rt="statediagram",me="state",ve=`${rt}-${me}`,zt="transition",be="note",De="note-edge",ke=`${zt} ${De}`,Ce=`${rt}-${be}`,Ae="cluster",xe=`${rt}-${Ae}`,Le="cluster-alt",Ie=`${rt}-${Le}`,Wt="parent",Kt="note",Oe="state",At="----",Re=`${At}${Kt}`,$t=`${At}${Wt}`,Xt=f((t,e=Bt)=>{if(!t.doc)return e;let r=e;for(const s of t.doc)s.stmt==="dir"&&(r=s.value);return r},"getDir"),Ne=f(function(t,e){return e.db.getClasses()},"getClasses"),we=f(function(t,e,r,s){return Ot(this,null,function*(){var m,T;v.info("REF0:"),v.info("Drawing state diagram (v2)",e);const{securityLevel:o,state:l,layout:p}=G();s.db.extract(s.db.getRootDocV2());const y=s.db.getData(),u=ee(e,o);y.type=s.type,y.layoutAlgorithm=p,y.nodeSpacing=(l==null?void 0:l.nodeSpacing)||50,y.rankSpacing=(l==null?void 0:l.rankSpacing)||50,y.markers=["barb"],y.diagramId=e,yield ie(y,u);const _=8;try{(typeof s.db.getLinks=="function"?s.db.getLinks():new Map).forEach(($,A)=>{var P;const k=typeof A=="string"?A:typeof(A==null?void 0:A.id)=="string"?A.id:"";if(!k){v.warn("⚠️ Invalid or missing stateId from key:",JSON.stringify(A));return}const d=(P=u.node())==null?void 0:P.querySelectorAll("g");let D;if(d==null||d.forEach(I=>{var Y;((Y=I.textContent)==null?void 0:Y.trim())===k&&(D=I)}),!D){v.warn("⚠️ Could not find node matching text:",k);return}const R=D.parentNode;if(!R){v.warn("⚠️ Node has no parent, cannot wrap:",k);return}const L=document.createElementNS("http://www.w3.org/2000/svg","a"),B=$.url.replace(/^"+|"+$/g,"");if(L.setAttributeNS("http://www.w3.org/1999/xlink","xlink:href",B),L.setAttribute("target","_blank"),$.tooltip){const I=$.tooltip.replace(/^"+|"+$/g,"");L.setAttribute("title",I)}R.replaceChild(L,D),L.appendChild(D),v.info("🔗 Wrapped node in <a> tag for:",k,$.url)})}catch(w){v.error("❌ Error injecting clickable links:",w)}re.insertTitle(u,"statediagramTitleText",(m=l==null?void 0:l.titleTopMargin)!=null?m:25,s.db.getDiagramTitle()),se(u,_,rt,(T=l==null?void 0:l.useMaxWidth)!=null?T:!0)})},"draw"),Ue={getClasses:Ne,draw:we,getDir:Xt},St=new Map,M=0;function yt(t="",e=0,r="",s=At){const o=r!==null&&r.length>0?`${s}${r}`:"";return`${Oe}-${t}${o}-${e}`}f(yt,"stateDomId");var $e=f((t,e,r,s,o,l,p,y)=>{v.trace("items",e),e.forEach(u=>{var _;switch(u.stmt){case q:st(t,u,r,s,o,l,p,y);break;case it:st(t,u,r,s,o,l,p,y);break;case Ct:{st(t,u.state1,r,s,o,l,p,y),st(t,u.state2,r,s,o,l,p,y);const m={id:"edge"+M,start:u.state1.id,end:u.state2.id,arrowhead:"normal",arrowTypeEnd:"arrow_barb",style:Vt,labelStyle:"",label:z.sanitizeText((_=u.description)!=null?_:"",G()),arrowheadStyle:Mt,labelpos:Ut,labelType:jt,thickness:Ht,classes:zt,look:p};o.push(m),M++}break}})},"setupDoc"),Pt=f((t,e=Bt)=>{let r=e;if(t.doc)for(const s of t.doc)s.stmt==="dir"&&(r=s.value);return r},"getDir");function et(t,e,r){if(!e.id||e.id==="</join></fork>"||e.id==="</choice>")return;e.cssClasses&&(Array.isArray(e.cssCompiledStyles)||(e.cssCompiledStyles=[]),e.cssClasses.split(" ").forEach(o=>{var p;const l=r.get(o);l&&(e.cssCompiledStyles=[...(p=e.cssCompiledStyles)!=null?p:[],...l.styles])}));const s=t.find(o=>o.id===e.id);s?Object.assign(s,e):t.push(e)}f(et,"insertOrUpdateNode");function Jt(t){var r;var e;return(r=(e=t==null?void 0:t.classes)==null?void 0:e.join(" "))!=null?r:""}f(Jt,"getClassesFromDbInfo");function qt(t){var e;return(e=t==null?void 0:t.styles)!=null?e:[]}f(qt,"getStylesFromDbInfo");var st=f((t,e,r,s,o,l,p,y)=>{var u,_,m;const T=e.id,w=r.get(T),$=Jt(w),A=qt(w),P=G();if(v.info("dataFetcher parsedItem",e,w,A),T!=="root"){let k=bt;e.start===!0?k=ge:e.start===!1&&(k=_e),e.type!==it&&(k=e.type),St.get(T)||St.set(T,{id:T,shape:k,description:z.sanitizeText(T,P),cssClasses:`${$} ${ve}`,cssStyles:A});const d=St.get(T);e.description&&(Array.isArray(d.description)?(d.shape=Dt,d.description.push(e.description)):(u=d.description)!=null&&u.length&&d.description.length>0?(d.shape=Dt,d.description===T?d.description=[e.description]:d.description=[d.description,e.description]):(d.shape=bt,d.description=e.description),d.description=z.sanitizeTextOrArray(d.description,P)),((_=d.description)==null?void 0:_.length)===1&&d.shape===Dt&&(d.type==="group"?d.shape=wt:d.shape=bt),!d.type&&e.doc&&(v.info("Setting cluster for XCX",T,Pt(e)),d.type="group",d.isGroup=!0,d.dir=Pt(e),d.shape=e.type===Ft?Nt:wt,d.cssClasses=`${d.cssClasses} ${xe} ${l?Ie:""}`);const D={labelStyle:"",shape:d.shape,label:d.description,cssClasses:d.cssClasses,cssCompiledStyles:[],cssStyles:d.cssStyles,id:T,dir:d.dir,domId:yt(T,M),type:d.type,isGroup:d.type==="group",padding:8,rx:10,ry:10,look:p};if(D.shape===Nt&&(D.label=""),t&&t.id!=="root"&&(v.trace("Setting node ",T," to be child of its parent ",t.id),D.parentId=t.id),D.centerLabel=!0,e.note){const R={labelStyle:"",shape:Te,label:e.note.text,cssClasses:Ce,cssStyles:[],cssCompiledStyles:[],id:T+Re+"-"+M,domId:yt(T,M,Kt),type:d.type,isGroup:d.type==="group",padding:(m=P.flowchart)==null?void 0:m.padding,look:p,position:e.note.position},L=T+$t,B={labelStyle:"",shape:Ee,label:e.note.text,cssClasses:d.cssClasses,cssStyles:[],id:T+$t,domId:yt(T,M,Wt),type:"group",isGroup:!0,padding:16,look:p,position:e.note.position};M++,B.id=L,R.parentId=L,et(s,B,y),et(s,R,y),et(s,D,y);let I=T,Y=R.id;e.note.position==="left of"&&(I=R.id,Y=T),o.push({id:I+"-"+Y,start:I,end:Y,arrowhead:"none",arrowTypeEnd:"",style:Vt,labelStyle:"",classes:ke,arrowheadStyle:Mt,labelpos:Ut,labelType:jt,thickness:Ht,look:p})}else et(s,D,y)}e.doc&&(v.trace("Adding nodes children "),$e(e,e.doc,r,s,o,!l,p,y))},"dataFetcher"),Pe=f(()=>{St.clear(),M=0},"reset"),C={START_NODE:"[*]",START_TYPE:"start",END_NODE:"[*]",END_TYPE:"end",COLOR_KEYWORD:"color",FILL_KEYWORD:"fill",BG_FILL:"bgFill",STYLECLASS_SEP:","},Yt=f(()=>new Map,"newClassesList"),Gt=f(()=>({relations:[],states:new Map,documents:{}}),"newDoc"),pt=f(t=>JSON.parse(JSON.stringify(t)),"clone"),je=(ft=class{constructor(t){this.version=t,this.nodes=[],this.edges=[],this.rootDoc=[],this.classes=Yt(),this.documents={root:Gt()},this.currentDocument=this.documents.root,this.startEndCount=0,this.dividerCnt=0,this.links=new Map,this.getAccTitle=ae,this.setAccTitle=ne,this.getAccDescription=le,this.setAccDescription=oe,this.setDiagramTitle=ce,this.getDiagramTitle=he,this.clear(),this.setRootDoc=this.setRootDoc.bind(this),this.getDividerId=this.getDividerId.bind(this),this.setDirection=this.setDirection.bind(this),this.trimColon=this.trimColon.bind(this)}extract(t){this.clear(!0);for(const s of Array.isArray(t)?t:t.doc)switch(s.stmt){case q:this.addState(s.id.trim(),s.type,s.doc,s.description,s.note);break;case Ct:this.addRelation(s.state1,s.state2,s.description);break;case pe:this.addStyleClass(s.id.trim(),s.classes);break;case Se:this.handleStyleDef(s);break;case ye:this.setCssClass(s.id.trim(),s.styleClass);break;case"click":this.addLink(s.id,s.url,s.tooltip);break}const e=this.getStates(),r=G();Pe(),st(void 0,this.getRootDocV2(),e,this.nodes,this.edges,!0,r.look,this.classes);for(const s of this.nodes)if(Array.isArray(s.label)){if(s.description=s.label.slice(1),s.isGroup&&s.description.length>0)throw new Error(`Group nodes can only have label. Remove the additional description for node [${s.id}]`);s.label=s.label[0]}}handleStyleDef(t){const e=t.id.trim().split(","),r=t.styleClass.split(",");for(const s of e){let o=this.getState(s);if(!o){const l=s.trim();this.addState(l),o=this.getState(l)}o&&(o.styles=r.map(l=>{var p;return(p=l.replace(/;/g,""))==null?void 0:p.trim()}))}}setRootDoc(t){v.info("Setting root doc",t),this.rootDoc=t,this.version===1?this.extract(t):this.extract(this.getRootDocV2())}docTranslator(t,e,r){if(e.stmt===Ct){this.docTranslator(t,e.state1,!0),this.docTranslator(t,e.state2,!1);return}if(e.stmt===q&&(e.id===C.START_NODE?(e.id=t.id+(r?"_start":"_end"),e.start=r):e.id=e.id.trim()),e.stmt!==J&&e.stmt!==q||!e.doc)return;const s=[];let o=[];for(const l of e.doc)if(l.type===Ft){const p=pt(l);p.doc=pt(o),s.push(p),o=[]}else o.push(l);if(s.length>0&&o.length>0){const l={stmt:q,id:ue(),type:"divider",doc:pt(o)};s.push(pt(l)),e.doc=s}e.doc.forEach(l=>this.docTranslator(e,l,!0))}getRootDocV2(){return this.docTranslator({id:J,stmt:J},{id:J,stmt:J,doc:this.rootDoc},!0),{id:J,doc:this.rootDoc}}addState(t,e=it,r=void 0,s=void 0,o=void 0,l=void 0,p=void 0,y=void 0){const u=t==null?void 0:t.trim();if(!this.currentDocument.states.has(u))v.info("Adding state ",u,s),this.currentDocument.states.set(u,{stmt:q,id:u,descriptions:[],type:e,doc:r,note:o,classes:[],styles:[],textStyles:[]});else{const _=this.currentDocument.states.get(u);if(!_)throw new Error(`State not found: ${u}`);_.doc||(_.doc=r),_.type||(_.type=e)}if(s&&(v.info("Setting state description",u,s),(Array.isArray(s)?s:[s]).forEach(m=>this.addDescription(u,m.trim()))),o){const _=this.currentDocument.states.get(u);if(!_)throw new Error(`State not found: ${u}`);_.note=o,_.note.text=z.sanitizeText(_.note.text,G())}l&&(v.info("Setting state classes",u,l),(Array.isArray(l)?l:[l]).forEach(m=>this.setCssClass(u,m.trim()))),p&&(v.info("Setting state styles",u,p),(Array.isArray(p)?p:[p]).forEach(m=>this.setStyle(u,m.trim()))),y&&(v.info("Setting state styles",u,p),(Array.isArray(y)?y:[y]).forEach(m=>this.setTextStyle(u,m.trim())))}clear(t){this.nodes=[],this.edges=[],this.documents={root:Gt()},this.currentDocument=this.documents.root,this.startEndCount=0,this.classes=Yt(),t||(this.links=new Map,de())}getState(t){return this.currentDocument.states.get(t)}getStates(){return this.currentDocument.states}logDocuments(){v.info("Documents = ",this.documents)}getRelations(){return this.currentDocument.relations}addLink(t,e,r){this.links.set(t,{url:e,tooltip:r}),v.warn("Adding link",t,e,r)}getLinks(){return this.links}startIdIfNeeded(t=""){return t===C.START_NODE?(this.startEndCount++,`${C.START_TYPE}${this.startEndCount}`):t}startTypeIfNeeded(t="",e=it){return t===C.START_NODE?C.START_TYPE:e}endIdIfNeeded(t=""){return t===C.END_NODE?(this.startEndCount++,`${C.END_TYPE}${this.startEndCount}`):t}endTypeIfNeeded(t="",e=it){return t===C.END_NODE?C.END_TYPE:e}addRelationObjs(t,e,r=""){const s=this.startIdIfNeeded(t.id.trim()),o=this.startTypeIfNeeded(t.id.trim(),t.type),l=this.startIdIfNeeded(e.id.trim()),p=this.startTypeIfNeeded(e.id.trim(),e.type);this.addState(s,o,t.doc,t.description,t.note,t.classes,t.styles,t.textStyles),this.addState(l,p,e.doc,e.description,e.note,e.classes,e.styles,e.textStyles),this.currentDocument.relations.push({id1:s,id2:l,relationTitle:z.sanitizeText(r,G())})}addRelation(t,e,r){if(typeof t=="object"&&typeof e=="object")this.addRelationObjs(t,e,r);else if(typeof t=="string"&&typeof e=="string"){const s=this.startIdIfNeeded(t.trim()),o=this.startTypeIfNeeded(t),l=this.endIdIfNeeded(e.trim()),p=this.endTypeIfNeeded(e);this.addState(s,o),this.addState(l,p),this.currentDocument.relations.push({id1:s,id2:l,relationTitle:r?z.sanitizeText(r,G()):void 0})}}addDescription(t,e){var r;const s=this.currentDocument.states.get(t),o=e.startsWith(":")?e.replace(":","").trim():e;(r=s==null?void 0:s.descriptions)==null||r.push(z.sanitizeText(o,G()))}cleanupLabel(t){return t.startsWith(":")?t.slice(2).trim():t.trim()}getDividerId(){return this.dividerCnt++,`divider-id-${this.dividerCnt}`}addStyleClass(t,e=""){this.classes.has(t)||this.classes.set(t,{id:t,styles:[],textStyles:[]});const r=this.classes.get(t);e&&r&&e.split(C.STYLECLASS_SEP).forEach(s=>{const o=s.replace(/([^;]*);/,"$1").trim();if(RegExp(C.COLOR_KEYWORD).exec(s)){const p=o.replace(C.FILL_KEYWORD,C.BG_FILL).replace(C.COLOR_KEYWORD,C.FILL_KEYWORD);r.textStyles.push(p)}r.styles.push(o)})}getClasses(){return this.classes}setCssClass(t,e){t.split(",").forEach(r=>{var s;let o=this.getState(r);if(!o){const l=r.trim();this.addState(l),o=this.getState(l)}(s=o==null?void 0:o.classes)==null||s.push(e)})}setStyle(t,e){var r,s;(s=(r=this.getState(t))==null?void 0:r.styles)==null||s.push(e)}setTextStyle(t,e){var r,s;(s=(r=this.getState(t))==null?void 0:r.textStyles)==null||s.push(e)}getDirectionStatement(){return this.rootDoc.find(t=>t.stmt===Rt)}getDirection(){var e;var t;return(e=(t=this.getDirectionStatement())==null?void 0:t.value)!=null?e:fe}setDirection(t){const e=this.getDirectionStatement();e?e.value=t:this.rootDoc.unshift({stmt:Rt,value:t})}trimColon(t){return t.startsWith(":")?t.slice(1).trim():t.trim()}getData(){const t=G();return{nodes:this.nodes,edges:this.edges,other:{},config:t,direction:Xt(this.getRootDocV2())}}getConfig(){return G().state}},f(ft,"StateDB"),ft.relationType={AGGREGATION:0,EXTENSION:1,COMPOSITION:2,DEPENDENCY:3},ft),Ye=f(t=>`
defs #statediagram-barbEnd {
    fill: ${t.transitionColor};
    stroke: ${t.transitionColor};
  }
g.stateGroup text {
  fill: ${t.nodeBorder};
  stroke: none;
  font-size: 10px;
}
g.stateGroup text {
  fill: ${t.textColor};
  stroke: none;
  font-size: 10px;

}
g.stateGroup .state-title {
  font-weight: bolder;
  fill: ${t.stateLabelColor};
}

g.stateGroup rect {
  fill: ${t.mainBkg};
  stroke: ${t.nodeBorder};
}

g.stateGroup line {
  stroke: ${t.lineColor};
  stroke-width: 1;
}

.transition {
  stroke: ${t.transitionColor};
  stroke-width: 1;
  fill: none;
}

.stateGroup .composit {
  fill: ${t.background};
  border-bottom: 1px
}

.stateGroup .alt-composit {
  fill: #e0e0e0;
  border-bottom: 1px
}

.state-note {
  stroke: ${t.noteBorderColor};
  fill: ${t.noteBkgColor};

  text {
    fill: ${t.noteTextColor};
    stroke: none;
    font-size: 10px;
  }
}

.stateLabel .box {
  stroke: none;
  stroke-width: 0;
  fill: ${t.mainBkg};
  opacity: 0.5;
}

.edgeLabel .label rect {
  fill: ${t.labelBackgroundColor};
  opacity: 0.5;
}
.edgeLabel {
  background-color: ${t.edgeLabelBackground};
  p {
    background-color: ${t.edgeLabelBackground};
  }
  rect {
    opacity: 0.5;
    background-color: ${t.edgeLabelBackground};
    fill: ${t.edgeLabelBackground};
  }
  text-align: center;
}
.edgeLabel .label text {
  fill: ${t.transitionLabelColor||t.tertiaryTextColor};
}
.label div .edgeLabel {
  color: ${t.transitionLabelColor||t.tertiaryTextColor};
}

.stateLabel text {
  fill: ${t.stateLabelColor};
  font-size: 10px;
  font-weight: bold;
}

.node circle.state-start {
  fill: ${t.specialStateColor};
  stroke: ${t.specialStateColor};
}

.node .fork-join {
  fill: ${t.specialStateColor};
  stroke: ${t.specialStateColor};
}

.node circle.state-end {
  fill: ${t.innerEndBackground};
  stroke: ${t.background};
  stroke-width: 1.5
}
.end-state-inner {
  fill: ${t.compositeBackground||t.background};
  // stroke: ${t.background};
  stroke-width: 1.5
}

.node rect {
  fill: ${t.stateBkg||t.mainBkg};
  stroke: ${t.stateBorder||t.nodeBorder};
  stroke-width: 1px;
}
.node polygon {
  fill: ${t.mainBkg};
  stroke: ${t.stateBorder||t.nodeBorder};;
  stroke-width: 1px;
}
#statediagram-barbEnd {
  fill: ${t.lineColor};
}

.statediagram-cluster rect {
  fill: ${t.compositeTitleBackground};
  stroke: ${t.stateBorder||t.nodeBorder};
  stroke-width: 1px;
}

.cluster-label, .nodeLabel {
  color: ${t.stateLabelColor};
  // line-height: 1;
}

.statediagram-cluster rect.outer {
  rx: 5px;
  ry: 5px;
}
.statediagram-state .divider {
  stroke: ${t.stateBorder||t.nodeBorder};
}

.statediagram-state .title-state {
  rx: 5px;
  ry: 5px;
}
.statediagram-cluster.statediagram-cluster .inner {
  fill: ${t.compositeBackground||t.background};
}
.statediagram-cluster.statediagram-cluster-alt .inner {
  fill: ${t.altBackground?t.altBackground:"#efefef"};
}

.statediagram-cluster .inner {
  rx:0;
  ry:0;
}

.statediagram-state rect.basic {
  rx: 5px;
  ry: 5px;
}
.statediagram-state rect.divider {
  stroke-dasharray: 10,10;
  fill: ${t.altBackground?t.altBackground:"#efefef"};
}

.note-edge {
  stroke-dasharray: 5;
}

.statediagram-note rect {
  fill: ${t.noteBkgColor};
  stroke: ${t.noteBorderColor};
  stroke-width: 1px;
  rx: 0;
  ry: 0;
}
.statediagram-note rect {
  fill: ${t.noteBkgColor};
  stroke: ${t.noteBorderColor};
  stroke-width: 1px;
  rx: 0;
  ry: 0;
}

.statediagram-note text {
  fill: ${t.noteTextColor};
}

.statediagram-note .nodeLabel {
  color: ${t.noteTextColor};
}
.statediagram .edgeLabel {
  color: red; // ${t.noteTextColor};
}

#dependencyStart, #dependencyEnd {
  fill: ${t.lineColor};
  stroke: ${t.lineColor};
  stroke-width: 1;
}

.statediagramTitleText {
  text-anchor: middle;
  font-size: 18px;
  fill: ${t.textColor};
}
`,"getStyles"),He=Ye;export{je as S,Me as a,Ue as b,He as s};
