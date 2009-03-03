Ext.onReady(function() {
    Ext.QuickTips.init();

    var scriptURI = "blog.esxx/";
    var postsURI  = "blog.esxx/posts/";

    function encodeXMLContent(value) {
	return value.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;")
    }

    var ajaxFailure = function(response, options) {
	var message = response.statusText;

	if (response.responseXML) {
	    message = Ext.DomQuery.selectValue('error', response.responseXML) || message;
	}

	Ext.Msg.show({
	    title:'Ajax request failed (' + response.status + ')',
	    msg: message,
	    buttons: Ext.Msg.ERROR,
	    icon: Ext.MessageBox.ERROR
	});
    }

    var post_store = new Ext.data.Store({
	url: postsURI,
	reader: new Ext.data.XmlReader(
	    {
		record: 'post',
		id: 'id'
	    },
	    [ 'id', 'title', 'created', 'updated', 'comments', { name: 'href', mapping: '@href' } ]
	)
    });

    var comment_store = new Ext.data.Store({
	url: scriptURI,
	reader: new Ext.data.XmlReader(
	    {
		record: 'comment',
		id: 'id'
	    },
	    [ 'id', 'body', 'created', 'updated', { name: 'href', mapping: '@href' } ]
	)
    });

    var dr = function(value, metadata, record, rowIndex, colIndex, store) {
	// Render dates without fractions
	var date = Date.parseDate(value, "Y-m-d H:i:s.u");
	return date.format("Y-m-d H:i:s");
    };

    var cr = function(value, metadata, record, rowIndex, colIndex, store) {
	// Remove HTML markup
	value = value.replace(/<\/?[^>]+>/g, "").replace(/&[^;]+;/, "");

	// Don't render more that the 30 first characters of a comment
	if (value.length > 30) {
	    return value.substr(0, 28) + ' &hellip;';
	}
	else {
	    return value;
	}
    }

    var post_list = new Ext.grid.GridPanel({
	store: post_store,
	columns: [
            { header: "ID",       dataIndex: 'id',       width: 20,  hidden: true,               },
            { header: "Title",    dataIndex: 'title',    width: 100,                             },
            { header: "Posted",   dataIndex: 'created',  width: 120,                renderer: dr },
            { header: "Updated",  dataIndex: 'updated',  width: 120, hidden: true,  renderer: dr },
            { header: "Comments", dataIndex: 'comments', width: 80,  hidden: true,               }
	],
	sm: new Ext.grid.RowSelectionModel({ singleSelect: true }),
	stripeRows: true,
	autoExpandColumn: 1,
    });

    post_list.getColumnModel().defaultSortable = true;
    post_list.getSelectionModel().on('selectionchange', function(index, ev) {
	var selected = post_list.getSelectionModel().getSelected();

	if (selected) {
	    var title = selected.get('title');
	    var href  = selected.get('href');

	    // This is ugly
	    comment_store.proxy.conn.url = href + '/';;
	    comment_store.reload();

	    Ext.getCmp('blog-title').setValue(title);
	    Ext.Ajax.request({
		method: "GET",
		url: href,
		success: function(response, options) {
		    var body = Ext.DomQuery.selectValue('body', response.responseXML);
		    Ext.getCmp('blog-body').setValue(body);
		},
		failure: ajaxFailure
	    });
	}
	else {
	    Ext.getCmp('blog-title').setValue('');
	    Ext.getCmp('blog-body').setValue('');
	    comment_store.removeAll();
	}
    });

    post_store.load();

    var comment_list = new Ext.grid.GridPanel({
	store: comment_store,
	columns: [
            { header: "ID",       dataIndex: 'id',       width: 20,  hidden: true,               },
            { header: "Excerpt",  dataIndex: 'body',     width: 100,                renderer: cr },
            { header: "Posted",   dataIndex: 'created',  width: 120,                renderer: dr },
            { header: "Updated",  dataIndex: 'updated',  width: 120, hidden: true,  renderer: dr },
	],
	sm: new Ext.grid.RowSelectionModel({ singleSelect: true }),
	stripeRows: true,
	autoExpandColumn: 1,
    });
    
    comment_list.on('rowdblclick', function(row, ev) {
	var selected = comment_list.getSelectionModel().getSelected();
			    
	if (selected) {
	    editComment(selected.get('href'));
	}
    });

    var editComment = function(href) {
        var win = new Ext.Window({
	    title: 'Edit comment',
            layout      : 'fit',
            width       : 500,
            height      : 300,
            closeAction :'hide',
            plain       : true,
            items       : {
 		id: 'comment-body',
		xtype:'htmleditor',
		name: 'body',
		anchor: '100% 100%'
	    },
            buttons: [
		{
                    text: 'Delete',
		    handler: function() {
			Ext.Ajax.request({
			    method: "DELETE",
			    url: href,
			    success: function(response, options) {
				comment_store.reload();
				win.hide();
			    },
			    failure: ajaxFailure
			});
		    }
                },

		{
                    text: 'Save',
		    handler: function() {
			var body  = encodeXMLContent(Ext.getCmp('comment-body').getValue());

			Ext.Ajax.request({
			    method: "PUT",
			    url: href,
			    xmlData: "<comment><body>" + body + "</body></comment>",
			    headers: { "Content-Type": "application/xml" },

			    success: function(response, options) {
				comment_store.reload();
				win.hide();
			    },
			    failure: ajaxFailure
			});
		    }
		},

		{
                    text: 'Cancel',
		    handler: function() {
			win.hide();
		    }
                }
	    ]
        });

	Ext.Ajax.request({
	    method: "GET",
	    url: href,
	    success: function(response, options) {
		var body = Ext.DomQuery.selectValue('body', response.responseXML);
		Ext.getCmp('comment-body').setValue(body);
	    },
	    failure: ajaxFailure
	});

        win.show(Ext.getCmp('comments'));
    }

    new Ext.Viewport({
	layout: 'border',
	items: [
	    {
		region: 'north',
		html: '<h1 class="x-panel-header">The Ajax Blog &mdash; Administration</h1>'
	    },

	    {
		region: 'west',
		xtype: 'panel',
		layout: 'border',
		width: 300,
		split: true,
		items: [
		    {
			region: 'center',
			title: 'Blog posts',
			layout: 'fit',
			items: post_list
		    },
		    
		    {
			region: 'south',
			id: 'comments',
			title: 'Comments (double-click to edit)',
			layout: 'fit',
			height: 300,
			split: true,
			items: comment_list,
		    }
		]
	    },

	    {
		region: 'center',
		xtype: 'form',
		bodyStyle: 'padding:5px 5px 0',
		frame: true,
		items: [
		    {
			layout: 'form',
			anchor: '100% 100%',
			hideLabels: true,
			items: [
			    {
				id: 'blog-title',
				xtype: 'textfield',
				name: 'title',
				anchor:'100%'
			    },

			    {
				id: 'blog-body',
				xtype:'htmleditor',
				name: 'body',
				anchor: '100% 100%'
			    }
			]
		    }
		],
		buttons: [
		    {
			text: 'Delete',
			handler: function() {
			    var selected = post_list.getSelectionModel().getSelected();
			    
			    if (selected) {
				Ext.Ajax.request({
				    method: "DELETE",
				    url: selected.get('href'),
				    success: function(response, options) {
					post_store.reload();
				    },
				    failure: ajaxFailure
				});
			    }
			}
		    },
		    {
			text: 'New',
			handler: function() {
			    post_list.getSelectionModel().clearSelections();
			}
		    },
		    {
			text: 'Save',
			handler: function() {
			    var selected = post_list.getSelectionModel().getSelected();
			    var href;
			    var method;
			    
			    if (selected) {
				href = selected.get('href');
				method = "PUT";
			    }
			    else {
				href   = postsURI;
				method = "POST";
			    }

			    var title = encodeXMLContent(Ext.getCmp('blog-title').getValue());
			    var body  = encodeXMLContent(Ext.getCmp('blog-body').getValue());

			    Ext.Ajax.request({
				method: method,
				url: href,
				xmlData: "<post><title>" + title + "</title>" +
				    "<body>" + body + "</body></post>",
				headers: { "Content-Type": "application/xml" },

				success: function(response, options) {
				    post_store.reload();
				},
				failure: ajaxFailure
			    });
			}
		    }
		]
	    }
	]
    });
});

